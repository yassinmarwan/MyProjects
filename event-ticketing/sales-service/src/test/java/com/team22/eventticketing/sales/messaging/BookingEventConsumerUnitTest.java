package com.team22.eventticketing.sales.messaging;

import tools.jackson.databind.ObjectMapper;
import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.messaging.consumers.BookingEventConsumer;
import com.team22.eventticketing.sales.messaging.publishers.PaymentEventPublisher;
import com.team22.eventticketing.sales.observer.MongoEventLogger;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingEventConsumerUnitTest {

    @Mock TicketSaleRepository ticketSaleRepository;
    @Mock PaymentEventPublisher paymentEventPublisher;
    @Mock MongoEventLogger auditLogger;

    // Real ObjectMapper — event records have no special types needing modules
    ObjectMapper objectMapper = new ObjectMapper();

    BookingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingEventConsumer(
                ticketSaleRepository, paymentEventPublisher, auditLogger, objectMapper);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Message toMessage(Object event) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(event);
        return new Message(body, new MessageProperties());
    }

    private TicketSale savedSale(Long bookingId, TicketSale.SaleStatus status) {
        TicketSale sale = new TicketSale(bookingId, 1L, 200.0,
                TicketSale.PaymentMethod.CREDIT_CARD, status);
        sale.setId(50L + bookingId);
        return sale;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // booking.completed → creates PENDING TicketSale + publishes payment.initiated
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("booking.completed: PENDING TicketSale saved with correct fields + payment.initiated published")
    void bookingCompleted_savesPendingSaleAndPublishesInitiated() throws Exception {
        BookingCompletedEvent event = new BookingCompletedEvent(100L, 1L, 5L, BigDecimal.valueOf(250.0));
        when(ticketSaleRepository.findByBookingId(100L)).thenReturn(Optional.empty());
        when(ticketSaleRepository.save(any())).thenAnswer(inv -> {
            TicketSale s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });

        consumer.handleBookingEvent(toMessage(event), "booking.completed");

        ArgumentCaptor<TicketSale> saleCaptor = ArgumentCaptor.forClass(TicketSale.class);
        verify(ticketSaleRepository).save(saleCaptor.capture());
        TicketSale saved = saleCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TicketSale.SaleStatus.PENDING);
        assertThat(saved.getBookingId()).isEqualTo(100L);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getAmount()).isEqualTo(250.0);

        verify(paymentEventPublisher).publishPaymentInitiated(eq(99L), eq(100L), eq(250.0));
    }

    @Test
    @DisplayName("booking.completed with null totalAmount: amount defaults to 0.0")
    void bookingCompleted_nullTotalAmount_defaultsToZero() throws Exception {
        BookingCompletedEvent event = new BookingCompletedEvent(101L, 1L, 5L, null);
        when(ticketSaleRepository.findByBookingId(101L)).thenReturn(Optional.empty());
        when(ticketSaleRepository.save(any())).thenAnswer(inv -> {
            TicketSale s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        consumer.handleBookingEvent(toMessage(event), "booking.completed");

        ArgumentCaptor<TicketSale> captor = ArgumentCaptor.forClass(TicketSale.class);
        verify(ticketSaleRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(0.0);
        verify(paymentEventPublisher).publishPaymentInitiated(anyLong(), eq(101L), eq(0.0));
    }

    @Test
    @DisplayName("Duplicate booking.completed: idempotency check skips save and publish")
    void bookingCompleted_duplicate_idempotentNoSecondSave() throws Exception {
        BookingCompletedEvent event = new BookingCompletedEvent(102L, 1L, 5L, BigDecimal.valueOf(300.0));
        TicketSale existing = savedSale(102L, TicketSale.SaleStatus.PENDING);
        when(ticketSaleRepository.findByBookingId(102L)).thenReturn(Optional.of(existing));

        consumer.handleBookingEvent(toMessage(event), "booking.completed");

        verify(ticketSaleRepository, never()).save(any());
        verifyNoInteractions(paymentEventPublisher);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // booking.cancelled → REFUNDED + payment.refunded
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("booking.cancelled: PENDING sale → REFUNDED with refundAmount + reason in transactionDetails")
    void bookingCancelled_pendingSale_refundsAndPublishes() throws Exception {
        BookingCancelledEvent event = new BookingCancelledEvent(200L, 2L, 6L, "customer_request");
        TicketSale pendingSale = savedSale(200L, TicketSale.SaleStatus.PENDING);
        pendingSale.setAmount(400.0);
        when(ticketSaleRepository.findAllByBookingId(200L)).thenReturn(List.of(pendingSale));
        when(ticketSaleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        ArgumentCaptor<TicketSale> captor = ArgumentCaptor.forClass(TicketSale.class);
        verify(ticketSaleRepository).save(captor.capture());
        TicketSale saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TicketSale.SaleStatus.REFUNDED);
        assertThat(saved.getTransactionDetails()).containsEntry("refundAmount", 400.0);
        assertThat(saved.getTransactionDetails()).containsEntry("refundReason", "customer_request");

        verify(paymentEventPublisher).publishPaymentRefunded(
                eq(pendingSale.getId()), eq(200L), eq(400.0));
    }

    @Test
    @DisplayName("booking.cancelled: COMPLETED sale → REFUNDED (late cancellation after payment)")
    void bookingCancelled_completedSale_refundsAndPublishes() throws Exception {
        BookingCancelledEvent event = new BookingCancelledEvent(201L, 2L, 6L, "event_cancelled");
        TicketSale completedSale = savedSale(201L, TicketSale.SaleStatus.COMPLETED);
        completedSale.setAmount(600.0);
        when(ticketSaleRepository.findAllByBookingId(201L)).thenReturn(List.of(completedSale));
        when(ticketSaleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        ArgumentCaptor<TicketSale> captor = ArgumentCaptor.forClass(TicketSale.class);
        verify(ticketSaleRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TicketSale.SaleStatus.REFUNDED);
        verify(paymentEventPublisher).publishPaymentRefunded(anyLong(), eq(201L), eq(600.0));
    }

    @Test
    @DisplayName("booking.cancelled: no refundable sale found → silent no-op, nothing published")
    void bookingCancelled_noRefundableSale_silentNoOp() throws Exception {
        BookingCancelledEvent event = new BookingCancelledEvent(202L, 2L, 6L, "test");
        TicketSale failedSale = savedSale(202L, TicketSale.SaleStatus.FAILED); // not refundable
        when(ticketSaleRepository.findAllByBookingId(202L)).thenReturn(List.of(failedSale));

        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        verify(ticketSaleRepository, never()).save(any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    @DisplayName("booking.cancelled: no sales at all → silent no-op")
    void bookingCancelled_noSales_silentNoOp() throws Exception {
        BookingCancelledEvent event = new BookingCancelledEvent(203L, 2L, 6L, "test");
        when(ticketSaleRepository.findAllByBookingId(203L)).thenReturn(List.of());

        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        verify(ticketSaleRepository, never()).save(any());
        verifyNoInteractions(paymentEventPublisher);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Unknown routing key — ack'd but ignored
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Unknown routing key booking.placed → no DB write, no publish, no exception")
    void unknownRoutingKey_isAckedAndIgnored() throws Exception {
        // booking.placed has no handler; the consumer silently acks it
        BookingCompletedEvent event = new BookingCompletedEvent(300L, 1L, 5L, BigDecimal.TEN);

        consumer.handleBookingEvent(toMessage(event), "booking.placed");

        verifyNoInteractions(ticketSaleRepository, paymentEventPublisher);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Malformed message body → RuntimeException (triggers DLQ in production)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Malformed JSON body → RuntimeException wrapping deserialization error")
    void malformedMessageBody_throwsRuntimeException() {
        Message badMessage = new Message("not-json".getBytes(), new MessageProperties());

        assertThatThrownBy(() ->
                consumer.handleBookingEvent(badMessage, "booking.completed"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("booking.completed");
    }
}
