package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.observer.EntityObserver;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.sales.strategy.*;
import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.dto.EventDTO;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RefundWindowPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RefundWindowPolicyService.class);

    private final TicketSaleRepository ticketSaleRepository;
    private final CacheEvictionService cacheEviction;
    private final BookingServiceClient bookingServiceClient;
    private final EventServiceClient eventServiceClient;
    private final List<EntityObserver> observers = new ArrayList<>();
    private final RefundStrategySelector strategySelector = new RefundStrategySelector();

    public RefundWindowPolicyService(TicketSaleRepository ticketSaleRepository,
                                     CacheEvictionService cacheEviction,
                                     BookingServiceClient bookingServiceClient,
                                     EventServiceClient eventServiceClient) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.cacheEviction = cacheEviction;
        this.bookingServiceClient = bookingServiceClient;
        this.eventServiceClient = eventServiceClient;
    }

    public void register(EntityObserver observer) { observers.add(observer); }
    public void unregister(EntityObserver observer) { observers.remove(observer); }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) o.onEvent(action, payload);
    }
    // S5-F12: M3 — Feign chain replaces 3-table JOIN (55-5679)
    @Transactional
    public TicketSale processRefundWithWindowPolicy(Long saleId, String reason) {

        MDC.put("saleId", saleId.toString());
        try {
            // 1. Find the sale
            TicketSale sale = ticketSaleRepository.findById(saleId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "TicketSale not found with id: " + saleId));

            // 2. Must be COMPLETED
            if (sale.getStatus() != TicketSale.SaleStatus.COMPLETED) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Only COMPLETED sales can be refunded");
            }

            // 3. M3: Feign → booking-service to get eventId
            MDC.put("bookingId", sale.getBookingId().toString());
            BookingDTO booking;
            try {
                log.info("Calling BookingServiceClient.getBooking with args={}", sale.getBookingId());
                booking = bookingServiceClient.getBooking(sale.getBookingId());
                log.info("BookingServiceClient.getBooking returned successfully");
            } catch (feign.FeignException.NotFound e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "booking has no associated event");
            } catch (feign.FeignException e) {
                log.warn("Feign call to booking-service failed: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Booking service temporarily unavailable");
            } finally {
                MDC.remove("bookingId");
            }

            if (booking.eventId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "booking has no associated event");
            }

            // 4. M3: Feign → event-service to get eventDate
            EventDTO event;
            try {
                log.info("Calling EventServiceClient.getEvent with args={}", booking.eventId());
                event = eventServiceClient.getEvent(booking.eventId());
                log.info("EventServiceClient.getEvent returned successfully");
            } catch (feign.FeignException.NotFound e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "booking has no associated event");
            } catch (feign.FeignException e) {
                log.warn("Feign call to event-service failed: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Event service temporarily unavailable");
            }

            LocalDateTime eventDate = event.eventDate();
            if (eventDate == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "booking has no associated event");
            }

            // 5. Compute hours until event and select strategy
            long hoursUntilEvent = Duration.between(LocalDateTime.now(), eventDate).toHours();
            RefundStrategy strategy = strategySelector.select(hoursUntilEvent);
            RefundRequest request = new RefundRequest(reason);
            RefundResult result = strategy.calculateRefund(sale, request, eventDate);

            // 6. NoRefundStrategy path
            if (strategy instanceof NoRefundStrategy) {
                notifyObservers("REFUND_DENIED", Map.of(
                        "saleId", saleId,
                        "method", sale.getMethod() != null ? sale.getMethod().name() : "",
                        "amount", sale.getAmount() != null ? sale.getAmount() : 0.0,
                        "strategyName", result.getStrategyName(),
                        "hoursUntilEvent", hoursUntilEvent
                ));
                cacheEviction.evictPattern("S5-F10");
                cacheEviction.evictDetail("S5-F11", saleId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refund window expired");
            }

            // 7. Full or Partial — update the sale
            sale.setStatus(TicketSale.SaleStatus.REFUNDED);

            Map<String, Object> details = new HashMap<>(
                    sale.getTransactionDetails() != null ? sale.getTransactionDetails() : new HashMap<>());
            details.put("refundAmount", result.getRefundAmount());
            details.put("refundPolicy", result.getStrategyName());
            details.put("refundReason", reason);
            details.put("refundedAt", LocalDateTime.now().toString());
            sale.setTransactionDetails(details);

            TicketSale saved = ticketSaleRepository.save(sale);
            log.info("TicketSale {} saved with status={}", saved.getId(), saved.getStatus());

            notifyObservers("SALE_REFUNDED", Map.of(
                    "saleId", saleId,
                    "method", saved.getMethod() != null ? saved.getMethod().name() : "",
                    "amount", saved.getAmount() != null ? saved.getAmount() : 0.0,
                    "strategyName", result.getStrategyName(),
                    "refundAmount", result.getRefundAmount(),
                    "hoursUntilEvent", hoursUntilEvent,
                    "reason", reason != null ? reason : ""
            ));

            cacheEviction.evictPattern("S5-F10");
            cacheEviction.evictDetail("S5-F11", saleId);
            cacheEviction.evictDetail("ticket-sale", saleId);

            return saved;

        } finally {
            MDC.remove("saleId");
        }
    }
}