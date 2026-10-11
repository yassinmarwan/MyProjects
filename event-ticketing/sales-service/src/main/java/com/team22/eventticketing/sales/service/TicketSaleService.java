package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.dto.AppliedPromotionDTO;
import com.team22.eventticketing.sales.dto.SaleDetailsDTO;
import com.team22.eventticketing.sales.dto.TierRevenueDTO;
import com.team22.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.observer.EntityObserver;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.contracts.dto.BookingItemDTO;
import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TicketSaleService {

    private static final Logger log = LoggerFactory.getLogger(TicketSaleService.class);

    private final TicketSaleRepository ticketSaleRepository;
    private final CacheEvictionService cacheEviction;
    private final UserServiceClient userServiceClient;
    private final BookingServiceClient bookingServiceClient;
    private final List<EntityObserver> observers = new ArrayList<>();

    public TicketSaleService(TicketSaleRepository ticketSaleRepository,
                             CacheEvictionService cacheEviction,
                             UserServiceClient userServiceClient,
                             BookingServiceClient bookingServiceClient) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.cacheEviction = cacheEviction;
        this.userServiceClient = userServiceClient;
        this.bookingServiceClient = bookingServiceClient;
    }

    public void register(EntityObserver observer) { observers.add(observer); }
    public void unregister(EntityObserver observer) { observers.remove(observer); }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) o.onEvent(action, payload);
    }

    // ─── Create ──────────────────────────────────────────────────────────────────

    public TicketSale create(TicketSale ticketSale) {
        TicketSale saved = ticketSaleRepository.save(ticketSale);
        Map<String, Object> createdPayload = new HashMap<>();
        createdPayload.put("saleId", saved.getId());
        if (saved.getMethod() != null)  createdPayload.put("method", saved.getMethod().name());
        if (saved.getAmount() != null)  createdPayload.put("amount", saved.getAmount());
        notifyObservers("SALE_CREATED", createdPayload);
        return saved;
    }

    // ─── [CRUD] Read by ID ────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "sales-service::ticket-sale", key = "#id")
    public TicketSale findById(Long id) {
        return ticketSaleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "TicketSale not found with id: " + id));
    }

    // ─── Read all ─────────────────────────────────────────────────────────────────

    public List<TicketSale> findAll() {
        return ticketSaleRepository.findAll();
    }

    // ─── [CRUD] Update ────────────────────────────────────────────────────────────

    public TicketSale update(Long id, TicketSale patch) {
        TicketSale existing = findById(id);

        if (patch.getBookingId() != null)           existing.setBookingId(patch.getBookingId());
        if (patch.getUserId() != null)              existing.setUserId(patch.getUserId());
        if (patch.getAmount() != null)              existing.setAmount(patch.getAmount());
        if (patch.getMethod() != null)              existing.setMethod(patch.getMethod());
        if (patch.getStatus() != null)              existing.setStatus(patch.getStatus());
        if (patch.getTransactionDetails() != null)  existing.setTransactionDetails(patch.getTransactionDetails());

        TicketSale saved = ticketSaleRepository.save(existing);
        cacheEviction.evictEntityAndFeatures("ticket-sale", id,
                "S5-F1", "S5-F3", "S5-F6", "S5-F8", "S5-F10", "S5-F11");
        notifyObservers("SALE_UPDATED", Map.of("saleId", saved.getId()));
        return saved;
    }

    // ─── [CRUD] Delete ────────────────────────────────────────────────────────────

    public void delete(Long id) {
        if (!ticketSaleRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "TicketSale not found with id: " + id);
        }
        ticketSaleRepository.deleteById(id);
        cacheEviction.evictEntityAndFeatures("ticket-sale", id,
                "S5-F1", "S5-F3", "S5-F6", "S5-F8", "S5-F10", "S5-F11");
        notifyObservers("SALE_DELETED", Map.of("saleId", id));
    }

    // ─── [S5-F2] Process Refund ───────────────────────────────────────────────────

    @Transactional
    public TicketSale refund(Long id, String reason) {
        TicketSale sale = findById(id);

        if (sale.getStatus() != TicketSale.SaleStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only COMPLETED sales can be refunded");
        }

        sale.setStatus(TicketSale.SaleStatus.REFUNDED);
        sale.getTransactionDetails().put("refundReason", reason);
        sale.getTransactionDetails().put("refundedAt", LocalDateTime.now().toString());

        TicketSale saved = ticketSaleRepository.save(sale);
        cacheEviction.evictEntityAndFeatures("ticket-sale", id,
                "S5-F1", "S5-F3", "S5-F6", "S5-F8", "S5-F10", "S5-F11");
        notifyObservers("SALE_REFUNDED", Map.of("saleId", saved.getId(),
                "reason", reason != null ? reason : ""));
        return saved;
    }

    // ─── [S5-F3] User Ticket Sale Summary — M3: Feign replaces SQL user check ────
// S5-F3: M3 — Feign replaces SQL user check (55-5679)

    @Cacheable(cacheNames = "sales-service::S5-F3", key = "#userId")
    public UserSaleSummaryDTO getUserSummary(Long userId) {
        MDC.put("userId", userId.toString());
        try {
            log.info("Calling UserServiceClient.getUser with args={}", userId);
            try {
                userServiceClient.getUser(userId);
                log.info("UserServiceClient.getUser returned successfully");
            } catch (feign.FeignException.NotFound e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + userId);
            } catch (feign.FeignException e) {
                log.warn("Feign call to user-service failed: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "User service temporarily unavailable");
            }

            List<Object[]> rows = ticketSaleRepository.findSalesSummaryByUserId(userId);

            Map<String, Double> methodBreakdown = new HashMap<>();
            long totalSales = 0;
            double totalAmount = 0.0;

            for (Object[] row : rows) {
                String method = (String) row[0];
                long count = ((Number) row[1]).longValue();
                double total = ((Number) row[2]).doubleValue();
                methodBreakdown.put(method, total);
                totalSales += count;
                totalAmount += total;
            }

            return UserSaleSummaryDTO.builder()
                    .userId(userId)
                    .totalSales(totalSales)
                    .totalAmount(totalAmount)
                    .methodBreakdown(methodBreakdown)
                    .build();
        } finally {
            MDC.remove("userId");
        }
    }

    // ─── [S5-F1] Search Sales ─────────────────────────────────────────────────────

    @Cacheable(cacheNames = "sales-service::S5-F1",
            key = "T(String).valueOf(#status) + ':' + T(String).valueOf(#startDate) + ':' + T(String).valueOf(#endDate)")
    public List<TicketSale> search(String status, String startDate, String endDate) {
        LocalDateTime start = (startDate != null)
                ? LocalDate.parse(startDate).atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        LocalDateTime end = (endDate != null)
                ? LocalDate.parse(endDate).atTime(23, 59, 59)
                : LocalDateTime.of(9999, 12, 31, 23, 59, 59);

        return ticketSaleRepository.findByStatusAndDateRange(status, start, end);
    }

    // ─── [S5-F7] Retry Failed Sale ────────────────────────────────────────────────

    @Transactional
    public TicketSale retry(Long id) {
        TicketSale sale = findById(id);

        if (sale.getStatus() != TicketSale.SaleStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only FAILED sales can be retried");
        }

        sale.setStatus(TicketSale.SaleStatus.COMPLETED);

        Map<String, Object> details = sale.getTransactionDetails();
        if (details == null) {
            details = new HashMap<>();
            sale.setTransactionDetails(details);
        }

        int retryAttempt = details.containsKey("retryAttempt")
                ? ((Number) details.get("retryAttempt")).intValue() + 1
                : 1;
        details.put("retryAttempt", retryAttempt);
        details.put("gatewayResponse", "approved");

        TicketSale saved = ticketSaleRepository.save(sale);
        cacheEviction.evictEntityAndFeatures("ticket-sale", id,
                "S5-F1", "S5-F3", "S5-F6", "S5-F8", "S5-F10", "S5-F11");
        Map<String, Object> retryPayload = new HashMap<>();
        retryPayload.put("saleId", saved.getId());
        if (saved.getMethod() != null) retryPayload.put("method", saved.getMethod().name());
        if (saved.getAmount() != null) retryPayload.put("amount", saved.getAmount());
        notifyObservers("SALE_RETRIED", retryPayload);
        notifyObservers("PAYMENT_PROCESSED", retryPayload);
        return saved;
    }

    // ─── [S5-F8] Sale Details with Applied Promotions ─────────────────────────────

    @Cacheable(cacheNames = "sales-service::S5-F8", key = "#saleId")
    @Transactional(readOnly = true)
    public SaleDetailsDTO getSaleDetails(Long saleId) {
        TicketSale sale = ticketSaleRepository.findByIdWithPromotions(saleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "TicketSale not found with id: " + saleId));

        List<AppliedPromotionDTO> appliedPromotions = sale.getSalePromotions().stream()
                .map(sp -> new AppliedPromotionDTO(
                        sp.getPromotion().getCode(),
                        sp.getPromotion().getDiscountType(),
                        sp.getDiscountApplied(),
                        sp.getAppliedAt()))
                .toList();

        double totalDiscount = appliedPromotions.stream()
                .mapToDouble(AppliedPromotionDTO::getDiscountApplied)
                .sum();

        double finalAmount = Math.max(0, sale.getAmount() - totalDiscount);

        return SaleDetailsDTO.builder()
                .saleId(sale.getId())
                .bookingId(sale.getBookingId())
                .userId(sale.getUserId())
                .originalAmount(sale.getAmount())
                .method(sale.getMethod())
                .status(sale.getStatus())
                .transactionDetails(sale.getTransactionDetails())
                .appliedPromotions(appliedPromotions)
                .totalDiscount(totalDiscount)
                .finalAmount(finalAmount)
                .build();
    }

    // ─── [S5-F10] Tier Revenue Analytics — M3: Feign replaces SQL JOIN ───────────

    public void logAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        notifyObservers("ANALYTICS_VIEWED", Map.of(
                "startDate", startDate.toString(),
                "endDate", endDate.toString()));
    }
    // S5-F10: M3 — Feign replaces SQL JOIN for tier analytics (55-5679)
    @Cacheable(cacheNames = "sales-service::S5-F10",
            key = "#startDate.toString() + ':' + #endDate.toString()")
    public List<TierRevenueDTO> getTierRevenue(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59, 999_000_000);

        List<TicketSale> sales = ticketSaleRepository.findCompletedSalesInRange(start, end);

        Map<String, Double> tierRevenue = new HashMap<>();
        Map<String, Long> tierSaleCount = new HashMap<>();
        Map<String, Long> tierTicketsSold = new HashMap<>();

        for (TicketSale sale : sales) {
            MDC.put("bookingId", sale.getBookingId().toString());
            try {
                log.info("Calling BookingServiceClient.getBookingItems with args={}", sale.getBookingId());
                List<BookingItemDTO> items;
                try {
                    items = bookingServiceClient.getBookingItems(sale.getBookingId());
                    log.info("BookingServiceClient.getBookingItems returned successfully");
                } catch (feign.FeignException e) {
                    log.warn("Feign call to booking-service failed for bookingId={}: {}",
                            sale.getBookingId(), e.getMessage());
                    continue;
                }

                for (BookingItemDTO item : items) {
                    String tier = (item.ticketTier() != null && !item.ticketTier().isBlank())
                            ? item.ticketTier() : "UNSPECIFIED";
                    double revenue = item.unitPrice().doubleValue() * item.quantity();

                    tierRevenue.merge(tier, revenue, Double::sum);
                    tierSaleCount.merge(tier, 1L, Long::sum);
                    tierTicketsSold.merge(tier, (long) item.quantity(), Long::sum);
                }
            } finally {
                MDC.remove("bookingId");
            }
        }

        List<TierRevenueDTO> result = new ArrayList<>();
        for (String tier : tierRevenue.keySet()) {
            double totalRev = tierRevenue.get(tier);
            long saleCount = tierSaleCount.get(tier);
            long ticketsSold = tierTicketsSold.get(tier);
            result.add(TierRevenueDTO.builder()
                    .tier(tier)
                    .totalRevenue(totalRev)
                    .saleCount(saleCount)
                    .ticketsSold(ticketsSold)
                    .averageRevenuePerSale(saleCount > 0 ? totalRev / saleCount : 0.0)
                    .build());
        }

        return result;
    }

    // [S5-F4] Process Booking Payment lives in SalesService.processBookingPayment
    // (the live POST /api/sales/booking/{bookingId} path). The previous duplicate
    // here was unreachable dead code and was removed during the S5-F4 refactor.
    // ─── [S5-F4] Process Booking Payment — M3: Feign replaces SQL booking check ──
// S5-F4: M3 — Feign replaces SQL booking check (55-5679)
    @Transactional
    public TicketSale processBookingPayment(Long bookingId, TicketSale.PaymentMethod method,
                                            String cardLastFour) {
        MDC.put("bookingId", bookingId.toString());
        try {
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

            if (!"PAYMENT_PENDING".equals(booking.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Booking is not awaiting payment. Status: " + booking.status());
            }

            TicketSale sale = ticketSaleRepository.findByBookingId(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "TicketSale not found for booking: " + bookingId));

            if (sale.getStatus() == TicketSale.SaleStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
            }

            sale.setMethod(method);
            sale.setStatus(TicketSale.SaleStatus.COMPLETED);

            if (sale.getTransactionDetails() == null) sale.setTransactionDetails(new HashMap<>());
            sale.getTransactionDetails().put("paymentMethod", method.toString());
            sale.getTransactionDetails().put("cardLastFour", cardLastFour);
            sale.getTransactionDetails().put("paidAt", LocalDateTime.now().toString());

            TicketSale saved = ticketSaleRepository.save(sale);
            log.info("TicketSale {} saved with status={}", saved.getId(), saved.getStatus());

            cacheEviction.evictEntityAndFeatures("ticket-sale", saved.getId(),
                    "S5-F1", "S5-F3", "S5-F6", "S5-F10", "S5-F11");
            notifyObservers("PAYMENT_PROCESSED", Map.of(
                    "saleId", saved.getId(), "bookingId", bookingId,
                    "method", method.name(),
                    "amount", saved.getAmount() != null ? saved.getAmount() : 0.0));
            return saved;
        } finally {
            MDC.remove("bookingId");
        }
    }

    // ─── [NEW] GET /api/sales/user/{userId}/total ─────────────────────────────────

    public BigDecimal getUserTotal(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate cannot be after endDate");
        }
        BigDecimal total = ticketSaleRepository
                .sumCompletedAmountByUserIdAndDateRange(userId, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }
}