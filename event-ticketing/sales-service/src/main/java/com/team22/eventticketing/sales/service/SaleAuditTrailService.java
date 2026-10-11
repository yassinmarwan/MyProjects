package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.dto.AuditEventDTO;
import com.team22.eventticketing.sales.dto.SaleAuditTrailDTO;
import com.team22.eventticketing.sales.mongo.PaymentAuditEvent;
import com.team22.eventticketing.sales.repository.PaymentAuditEventRepository;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class SaleAuditTrailService {

    private final TicketSaleRepository ticketSaleRepository;
    private final PaymentAuditEventRepository paymentAuditEventRepository;

    public SaleAuditTrailService(TicketSaleRepository ticketSaleRepository,
                                  PaymentAuditEventRepository paymentAuditEventRepository) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.paymentAuditEventRepository = paymentAuditEventRepository;
    }

    @Cacheable(cacheNames = "sales-service::S5-F11", key = "#saleId")
    public SaleAuditTrailDTO getAuditTrail(Long saleId) {
        if (!ticketSaleRepository.existsById(saleId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "TicketSale not found with id: " + saleId);
        }

        List<PaymentAuditEvent> events = paymentAuditEventRepository
                .findBySaleIdAndActionNotOrderByTimestampAsc(saleId, "ANALYTICS_VIEWED");

        List<AuditEventDTO> auditEvents = events.stream()
                .map(e -> new AuditEventDTO(
                        normalizeAction(e.getAction()),
                        e.getTimestamp() != null
                                ? LocalDateTime.ofInstant(e.getTimestamp(), ZoneOffset.UTC)
                                : null,
                        e.getMethod(),
                        e.getAmount(),
                        e.getDetails()
                ))
                .toList();

        return new SaleAuditTrailDTO(saleId, auditEvents);
    }

    private static String normalizeAction(String action) {
        return switch (action) {
            case "SALE_CREATED"                -> "CREATED";
            case "PAYMENT_PROCESSED"           -> "COMPLETED";
            case "SALE_FAILED"                 -> "FAILED";
            case "SALE_RETRIED"                -> "RETRY_ATTEMPTED";
            case "SALE_REFUNDED",
                 "SALE_REFUNDED_WINDOW_POLICY" -> "REFUNDED";
            default                            -> action;
        };
    }
}
