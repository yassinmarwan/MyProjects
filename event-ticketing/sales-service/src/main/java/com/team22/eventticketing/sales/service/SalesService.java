package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.dto.RevenueReportDTO;
import com.team22.eventticketing.sales.entity.Promotion;
import com.team22.eventticketing.sales.entity.SalePromotion;
import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.messaging.publishers.PaymentEventPublisher;
import com.team22.eventticketing.sales.observer.EntityObserver;
import com.team22.eventticketing.sales.repository.PromotionRepository;
import com.team22.eventticketing.sales.repository.SalePromotionRepository;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesService {

    private static final Logger log = LoggerFactory.getLogger(SalesService.class);

    private final TicketSaleRepository ticketSaleRepository;
    private final PromotionRepository promotionRepository;
    private final SalePromotionRepository salePromotionRepository;
    private final CacheEvictionService cacheEviction;
    private final BookingServiceClient bookingServiceClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private final List<EntityObserver> observers = new ArrayList<>();

    public SalesService(TicketSaleRepository ticketSaleRepository,
                        PromotionRepository promotionRepository,
                        SalePromotionRepository salePromotionRepository,
                        CacheEvictionService cacheEviction,
                        BookingServiceClient bookingServiceClient,
                        PaymentEventPublisher paymentEventPublisher) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.promotionRepository = promotionRepository;
        this.salePromotionRepository = salePromotionRepository;
        this.cacheEviction = cacheEviction;
        this.bookingServiceClient = bookingServiceClient;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    public void register(EntityObserver observer) { observers.add(observer); }
    public void unregister(EntityObserver observer) { observers.remove(observer); }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) o.onEvent(action, payload);
    }

    // ─── [S5-F4] Process booking payment — M3: Feign booking-status validation ───
    //     + publishes payment.completed / payment.failed to payment.events
    @Transactional
    public TicketSale processBookingPayment(Long bookingId, TicketSale.PaymentMethod method,
                                            String cardLastFour, boolean simulateFailure) {

        MDC.put("bookingId", bookingId.toString());
        try {
            // M3: replace native SELECT * FROM bookings with Feign → booking-service
            log.info("Calling BookingServiceClient.getBooking with args={}", bookingId);
            BookingDTO booking;
            try {
                booking = bookingServiceClient.getBooking(bookingId);
                log.info("BookingServiceClient.getBooking returned successfully");
            } catch (feign.FeignException.NotFound e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking not found: " + bookingId);
            } catch (feign.FeignException e) {
                log.warn("Feign call to booking-service failed: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Booking service temporarily unavailable");
            }

            // In the M3 saga context the booking must be at the customer-payment step
            if (!"PAYMENT_PENDING".equals(booking.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Booking is not awaiting payment. Status: " + booking.status());
            }

            // Find the existing PENDING TicketSale created by the booking.completed consumer
            TicketSale ticketSale = ticketSaleRepository.findByBookingIdAndStatusPending(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Pending ticket sale not found for booking: " + bookingId));

            Map<String, Object> transactionDetails = new HashMap<>();
            transactionDetails.put("method", method.name());
            if (cardLastFour != null) transactionDetails.put("cardLastFour", cardLastFour);

            if (simulateFailure) {
                ticketSale.setStatus(TicketSale.SaleStatus.FAILED);
                transactionDetails.put("gatewayResponse", "declined");
                transactionDetails.put("failedAt", LocalDateTime.now().toString());
                ticketSale.setTransactionDetails(transactionDetails);
                TicketSale saved = ticketSaleRepository.save(ticketSale);
                log.info("TicketSale {} saved with status={}", saved.getId(), saved.getStatus());
                cacheEviction.evictEntityAndFeatures("ticket-sale", saved.getId(),
                        "S5-F1", "S5-F3", "S5-F6", "S5-F10", "S5-F11");
                notifyObservers("SALE_FAILED", Map.of("saleId", saved.getId(), "bookingId", bookingId,
                        "method", method.name(), "amount", saved.getAmount() != null ? saved.getAmount() : 0.0));
                paymentEventPublisher.publishPaymentFailed(saved.getId(), bookingId,
                        "payment declined");
                return saved;
            }

            ticketSale.setMethod(method);
            ticketSale.setStatus(TicketSale.SaleStatus.COMPLETED);
            transactionDetails.put("gatewayResponse", "approved");
            transactionDetails.put("paidAt", LocalDateTime.now().toString());
            ticketSale.setTransactionDetails(transactionDetails);

            TicketSale saved = ticketSaleRepository.save(ticketSale);
            log.info("TicketSale {} saved with status={}", saved.getId(), saved.getStatus());
            cacheEviction.evictEntityAndFeatures("ticket-sale", saved.getId(),
                    "S5-F1", "S5-F3", "S5-F6", "S5-F10", "S5-F11");
            notifyObservers("PAYMENT_PROCESSED", Map.of("saleId", saved.getId(), "bookingId", bookingId,
                    "method", method.name(), "amount", saved.getAmount() != null ? saved.getAmount() : 0.0));
            paymentEventPublisher.publishPaymentCompleted(saved.getId(), bookingId,
                    saved.getAmount() != null ? saved.getAmount() : 0.0);
            return saved;
        } finally {
            MDC.remove("bookingId");
        }
    }

    public TicketSale processBookingPayment(Long bookingId, TicketSale.PaymentMethod method,
                                            String cardLastFour) {
        return processBookingPayment(bookingId, method, cardLastFour, false);
    }

    // ─── [S5-F6] Revenue Report (report — cached) ────────────────────────────────

    @Cacheable(cacheNames = "sales-service::S5-F6",
            key = "T(String).valueOf(#startDate) + ':' + T(String).valueOf(#endDate)")
    public RevenueReportDTO getRevenueReport(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate cannot be after endDate");
        }

        List<TicketSale> completed = ticketSaleRepository
                .findByStatusAndCreatedAtBetween("COMPLETED", startDate, endDate);

        double totalRevenue = completed.stream()
                .mapToDouble(TicketSale::getAmount)
                .sum();

        int totalTransactions = completed.size();

        double averageSale = totalTransactions == 0 ? 0 : totalRevenue / totalTransactions;

        List<TicketSale> refunded = ticketSaleRepository
                .findByStatusAndCreatedAtBetween("REFUNDED", startDate, endDate);

        double refundedAmount = refunded.stream()
                .mapToDouble(TicketSale::getAmount)
                .sum();

        int refundCount = refunded.size();

        return RevenueReportDTO.builder()
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTransactions)
                .averageSale(averageSale)
                .refundedAmount(refundedAmount)
                .refundCount(refundCount)
                .build();
    }

    // ─── [S5-F5] Apply Promotion to Sale (write — invalidates) ───────────────────
    @Transactional
    public TicketSale applyPromotionToSale(Long saleId, Long promotionId) {

        TicketSale ticketSale = ticketSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ticket sale not found"));

        if (ticketSale.getStatus() != TicketSale.SaleStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot apply promotion to a completed/cancelled sale");
        }

        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Promotion not found"));

        if (!promotion.getActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion is inactive");
        }

        if (promotion.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion has expired");
        }

        if (promotion.getCurrentUses() >= promotion.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion usage limit reached");
        }

        boolean alreadyApplied = salePromotionRepository
                .existsByTicketSaleIdAndPromotionId(saleId, promotionId);

        if (alreadyApplied) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "promotion already applied");
        }

        SalePromotion salePromo = createSalePromotion(ticketSale, promotion);

        promotion.setCurrentUses(promotion.getCurrentUses() + 1);

        salePromotionRepository.save(salePromo);
        promotionRepository.save(promotion);

        ticketSale.getSalePromotions().add(salePromo);

        cacheEviction.evictEntityAndFeatures("ticket-sale", saleId,
                "S5-F8", "S5-F10", "S5-F11");
        cacheEviction.evictEntityAndFeatures("promotion", promotionId, "S5-F9");
        notifyObservers("PROMOTION_APPLIED", Map.of("saleId", saleId, "promotionId", promotionId));
        return ticketSale;
    }

    private SalePromotion createSalePromotion(TicketSale sale, Promotion promotion) {

        double discount;

        if (promotion.getDiscountType() == Promotion.DiscountType.PERCENTAGE) {
            discount = sale.getAmount() * promotion.getDiscountValue() / 100.0;
            discount = Math.min(discount, sale.getAmount());
        } else {
            discount = Math.min(promotion.getDiscountValue(), sale.getAmount());
        }

        SalePromotion salePromo = new SalePromotion();
        salePromo.setTicketSale(sale);
        salePromo.setPromotion(promotion);
        salePromo.setDiscountApplied(discount);
        salePromo.setAppliedAt(LocalDateTime.now());

        return salePromo;
    }
}