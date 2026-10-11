package com.team22.eventticketing.sales.messaging.consumers;

import tools.jackson.databind.ObjectMapper;
import com.team22.eventticketing.sales.config.PaymentEventConfig;
import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.messaging.publishers.PaymentEventPublisher;
import com.team22.eventticketing.sales.observer.MongoEventLogger;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import jakarta.transaction.Transactional;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BookingEventConsumer {

    private final TicketSaleRepository ticketSaleRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final MongoEventLogger auditLogger;
    private final ObjectMapper objectMapper;

    public BookingEventConsumer(TicketSaleRepository ticketSaleRepository,
                                PaymentEventPublisher paymentEventPublisher,
                                MongoEventLogger auditLogger,
                                ObjectMapper objectMapper) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = PaymentEventConfig.SAGA_QUEUE)
    @Transactional
    public void handleBookingEvent(Message message,
                                   @Header("amqp_receivedRoutingKey") String routingKey) {
        try {
            if ("booking.completed".equals(routingKey)) {
                handleCompleted(objectMapper.readValue(
                        message.getBody(), BookingCompletedEvent.class));
            } else if ("booking.cancelled".equals(routingKey)) {
                handleCancelled(objectMapper.readValue(
                        message.getBody(), BookingCancelledEvent.class));
            }
            // any other booking.* routing key: ignore (still ack'd)
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to process " + routingKey + ": " + e.getMessage(), e);
        }
    }

    private void handleCompleted(BookingCompletedEvent event) {
        // Idempotency: a sale already exists for this booking → no-op.
        if (ticketSaleRepository.findByBookingId(event.bookingId()).isPresent()) {
            return;
        }

        double amount = event.totalAmount() != null
                ? event.totalAmount().doubleValue() : 0.0;

        // method is NOT-NULL in schema; real method is chosen at S5-F4.
        // CREDIT_CARD is a placeholder (see plan §0 "Resolved decision").
        TicketSale sale = new TicketSale(
                event.bookingId(),
                event.userId(),
                amount,
                TicketSale.PaymentMethod.CREDIT_CARD,
                TicketSale.SaleStatus.PENDING);

        TicketSale saved = ticketSaleRepository.save(sale);

        paymentEventPublisher.publishPaymentInitiated(
                saved.getId(), saved.getBookingId(), amount);

        audit("SALE_CREATED", saved, amount);
    }

    private void handleCancelled(BookingCancelledEvent event) {
        List<TicketSale> sales = ticketSaleRepository.findAllByBookingId(event.bookingId());

        TicketSale sale = sales.stream()
                .filter(s -> s.getStatus() == TicketSale.SaleStatus.PENDING
                          || s.getStatus() == TicketSale.SaleStatus.COMPLETED)
                .findFirst()
                .orElse(null);

        if (sale == null) {
            return; // nothing refundable for this booking
        }

        double amount = sale.getAmount() != null ? sale.getAmount() : 0.0;

        Map<String, Object> td = sale.getTransactionDetails() != null
                ? sale.getTransactionDetails() : new HashMap<>();
        td.put("refundAmount", amount);
        td.put("refundReason", event.reason());
        sale.setTransactionDetails(td);
        sale.setStatus(TicketSale.SaleStatus.REFUNDED);

        TicketSale saved = ticketSaleRepository.save(sale);

        paymentEventPublisher.publishPaymentRefunded(
                saved.getId(), saved.getBookingId(), amount);

        audit("SALE_REFUNDED", saved, amount);
    }

    private void audit(String action, TicketSale sale, double amount) {
        Map<String, Object> params = new HashMap<>();
        params.put("saleId", sale.getId());
        params.put("bookingId", sale.getBookingId());
        params.put("amount", amount);
        params.put("method", sale.getMethod() != null ? sale.getMethod().name() : null);
        auditLogger.onEvent(action, params);
    }
}
