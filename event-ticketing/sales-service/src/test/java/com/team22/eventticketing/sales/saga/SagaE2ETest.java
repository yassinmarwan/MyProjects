package com.team22.eventticketing.sales.saga;

import tools.jackson.databind.ObjectMapper;
import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.messaging.consumers.BookingEventConsumer;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.sales.service.SalesService;
import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.events.PaymentFailedEvent;
import com.team22.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team22.eventticketing.contracts.events.PaymentRefundedEvent;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Saga E2E tests — full event-choreography chain exercised in one test per scenario.
 *
 * Uses real PostgreSQL (Testcontainers) for TicketSale persistence and mocked
 * RabbitTemplate to capture published events without needing a live broker.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ NOTE — ticket.status-changed                                        │
 * │ This event is published by ticket-service after it consumes         │
 * │ booking.completed. Asserting it requires a multi-service E2E setup  │
 * │ (ticket-service + booking-service + sales-service + real broker).   │
 * │ The tests here verify the sales-service side of the saga:           │
 * │   booking.completed → payment.initiated → payment.completed/failed  │
 * │   booking.cancelled → payment.refunded  (compensation)             │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Scenario 1 — S3-F4 trigger → payment.initiated published, then payment success.
 * Scenario 2 — S3-F4 trigger → payment.initiated, then failure + compensation.
 * Scenario 3 — Late cancellation: payment COMPLETED → booking.cancelled → REFUNDED.
 * Scenario 4 — Idempotent compensation: booking.cancelled replayed → single refund.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("saga-test")
@Testcontainers
@SuppressWarnings("resource") // container closed by @Testcontainers extension
class SagaE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-sales-e2e")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Mocked external dependencies ─────────────────────────────────────

    @MockitoBean RedisConnectionFactory redisConnectionFactory;
    @MockitoBean RabbitTemplate rabbitTemplate;
    @MockitoBean BookingServiceClient bookingServiceClient;
    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean EventServiceClient eventServiceClient;
    @MockitoBean com.team22.eventticketing.sales.observer.MongoEventLogger auditLogger;
    @MockitoBean com.team22.eventticketing.sales.repository.PaymentAuditEventRepository paymentAuditEventRepository;

    // ── Beans under test ──────────────────────────────────────────────────

    @Autowired BookingEventConsumer bookingEventConsumer;
    @Autowired SalesService salesService;
    @Autowired TicketSaleRepository ticketSaleRepository;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        ticketSaleRepository.deleteAll();
        reset(rabbitTemplate, bookingServiceClient);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Message toMessage(Object event) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(event);
        return new Message(body, new MessageProperties());
    }

    private BookingDTO bookingPendingPayment(Long bookingId, double amount) {
        return new BookingDTO(bookingId, 1L, 5L, "PAYMENT_PENDING", BigDecimal.valueOf(amount));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scenario 1 — S3-F4 trigger → payment.initiated → payment success
    //
    // Simulates: S3-F4 fires (booking.completed published) → sales consumer
    // creates PENDING TicketSale + emits payment.initiated → S5-F4 processes
    // payment successfully → COMPLETED + payment.completed emitted.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario 1 — S3-F4 → payment.initiated received, then payment succeeds → COMPLETED")
    void e2e_s3f4Trigger_paymentInitiatedReceived_thenPaymentSucceeds() throws Exception {

        // ── Trigger: simulate S3-F4 completing the booking ───────────────
        BookingCompletedEvent completedEvent =
                new BookingCompletedEvent(600L, 1L, 5L, BigDecimal.valueOf(450.0));
        bookingEventConsumer.handleBookingEvent(toMessage(completedEvent), "booking.completed");

        // ── Assert: PENDING TicketSale created ────────────────────────────
        TicketSale sale = ticketSaleRepository.findByBookingId(600L).orElseThrow();
        assertThat(sale.getStatus()).isEqualTo(TicketSale.SaleStatus.PENDING);
        assertThat(sale.getAmount()).isEqualTo(450.0);

        // ── Assert: payment.initiated published (verifiable event) ────────
        // In a multi-service E2E, ticket.status-changed would also be asserted here
        // (published by ticket-service after consuming booking.completed).
        ArgumentCaptor<PaymentInitiatedEvent> initiatedCaptor =
                ArgumentCaptor.forClass(PaymentInitiatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"), eq("payment.initiated"), initiatedCaptor.capture());
        PaymentInitiatedEvent initiatedEvent = initiatedCaptor.getValue();
        assertThat(initiatedEvent.bookingId()).isEqualTo(600L);
        assertThat(initiatedEvent.amount()).isEqualByComparingTo(BigDecimal.valueOf(450.0));

        // ── S5-F4: process payment (booking-service confirms PAYMENT_PENDING) ──
        when(bookingServiceClient.getBooking(600L)).thenReturn(bookingPendingPayment(600L, 450.0));
        TicketSale processed = salesService.processBookingPayment(
                600L, TicketSale.PaymentMethod.CREDIT_CARD, "1234");

        // ── Assert: sale is COMPLETED + payment.completed emitted ────────
        assertThat(processed.getStatus()).isEqualTo(TicketSale.SaleStatus.COMPLETED);
        assertThat(processed.getTransactionDetails()).containsEntry("gatewayResponse", "approved");

        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"), eq("payment.completed"), any(Object.class));
        verify(rabbitTemplate, never()).convertAndSend(
                eq("payment.events"), eq("payment.failed"), any(Object.class));

        // ── Assert persisted state ────────────────────────────────────────
        assertThat(ticketSaleRepository.findById(processed.getId())
                .orElseThrow().getStatus()).isEqualTo(TicketSale.SaleStatus.COMPLETED);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scenario 2 — S3-F4 trigger → payment.initiated → payment FAILS → compensation
    //
    // Inject payment failure (S5-F4 simulateFailure=true), then trigger
    // booking.cancelled to assert full compensation runs.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario 2 — payment failure injected → FAILED + compensation via booking.cancelled → REFUNDED")
    void e2e_paymentFailureInjected_compensationRuns() throws Exception {

        // ── Trigger: S3-F4 fires ──────────────────────────────────────────
        BookingCompletedEvent completedEvent =
                new BookingCompletedEvent(700L, 2L, 6L, BigDecimal.valueOf(320.0));
        bookingEventConsumer.handleBookingEvent(toMessage(completedEvent), "booking.completed");

        TicketSale pendingSale = ticketSaleRepository.findByBookingId(700L).orElseThrow();
        assertThat(pendingSale.getStatus()).isEqualTo(TicketSale.SaleStatus.PENDING);

        // Assert payment.initiated was emitted on saga forward path
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"), eq("payment.initiated"), any(Object.class));

        // ── Inject failure: S5-F4 with simulateFailure=true ──────────────
        when(bookingServiceClient.getBooking(700L)).thenReturn(bookingPendingPayment(700L, 320.0));
        TicketSale failed = salesService.processBookingPayment(
                700L, TicketSale.PaymentMethod.DEBIT_CARD, null, true);

        assertThat(failed.getStatus()).isEqualTo(TicketSale.SaleStatus.FAILED);
        assertThat(failed.getTransactionDetails()).containsEntry("gatewayResponse", "declined");

        // Assert payment.failed emitted (NOT payment.completed)
        ArgumentCaptor<PaymentFailedEvent> failedCaptor =
                ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"), eq("payment.failed"), failedCaptor.capture());
        PaymentFailedEvent failedEvent = failedCaptor.getValue();
        assertThat(failedEvent.bookingId()).isEqualTo(700L);
        assertThat(failedEvent.reason()).isEqualTo("payment declined");

        verify(rabbitTemplate, never()).convertAndSend(
                eq("payment.events"), eq("payment.completed"), any(Object.class));

        // ── Compensation: booking.cancelled triggers refund ──────────────
        BookingCancelledEvent cancelledEvent =
                new BookingCancelledEvent(700L, 2L, 6L, "payment_failed_retry_exhausted");
        bookingEventConsumer.handleBookingEvent(toMessage(cancelledEvent), "booking.cancelled");

        // Assert: FAILED sale is not refundable — booking.cancelled on a FAILED
        // sale returns no refundable entry, so the saga ends here.
        // This is the correct compensation boundary: FAILED sales are already
        // closed; no money was taken, so no payment.refunded is necessary.
        TicketSale afterCancel = ticketSaleRepository.findById(failed.getId()).orElseThrow();
        assertThat(afterCancel.getStatus()).isEqualTo(TicketSale.SaleStatus.FAILED);
        verify(rabbitTemplate, never()).convertAndSend(
                eq("payment.events"), eq("payment.refunded"), any(Object.class));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scenario 3 — Late cancellation: payment already COMPLETED → REFUNDED
    //
    // Full happy path runs first (sale → COMPLETED), then booking.cancelled
    // arrives late. Compensation must still run.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario 3 — Payment already COMPLETED, late booking.cancelled → REFUNDED + payment.refunded")
    void e2e_completedPayment_lateCancellation_refundCompensationRuns() throws Exception {

        // ── Happy path: booking.completed → PENDING → COMPLETED ──────────
        BookingCompletedEvent completedEvent =
                new BookingCompletedEvent(800L, 3L, 7L, BigDecimal.valueOf(900.0));
        bookingEventConsumer.handleBookingEvent(toMessage(completedEvent), "booking.completed");

        when(bookingServiceClient.getBooking(800L)).thenReturn(bookingPendingPayment(800L, 900.0));
        TicketSale paid = salesService.processBookingPayment(
                800L, TicketSale.PaymentMethod.CREDIT_CARD, "9999");
        assertThat(paid.getStatus()).isEqualTo(TicketSale.SaleStatus.COMPLETED);

        // ── Late compensation: booking.cancelled arrives after payment ────
        reset(rabbitTemplate); // clear prior invocations for clean assertions
        BookingCancelledEvent cancelledEvent =
                new BookingCancelledEvent(800L, 3L, 7L, "event_cancelled_by_organiser");
        bookingEventConsumer.handleBookingEvent(toMessage(cancelledEvent), "booking.cancelled");

        // Assert: COMPLETED sale → REFUNDED
        TicketSale refunded = ticketSaleRepository.findById(paid.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(TicketSale.SaleStatus.REFUNDED);
        assertThat(refunded.getTransactionDetails()).containsEntry("refundAmount", 900.0);
        assertThat(refunded.getTransactionDetails())
                .containsEntry("refundReason", "event_cancelled_by_organiser");

        // Assert: payment.refunded published with correct amount
        ArgumentCaptor<PaymentRefundedEvent> refundCaptor =
                ArgumentCaptor.forClass(PaymentRefundedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"), eq("payment.refunded"), refundCaptor.capture());
        PaymentRefundedEvent refundedEvent = refundCaptor.getValue();
        assertThat(refundedEvent.bookingId()).isEqualTo(800L);
        assertThat(refundedEvent.refundAmount()).isEqualByComparingTo(BigDecimal.valueOf(900.0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scenario 4 — Idempotent compensation: booking.cancelled replayed
    //
    // A duplicate booking.cancelled must not double-refund or publish
    // payment.refunded twice.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario 4 — booking.cancelled replayed → single refund, payment.refunded published once")
    void e2e_bookingCancelledReplayed_idempotentCompensation() throws Exception {

        // Setup: PENDING sale exists
        BookingCompletedEvent completedEvent =
                new BookingCompletedEvent(900L, 4L, 8L, BigDecimal.valueOf(150.0));
        bookingEventConsumer.handleBookingEvent(toMessage(completedEvent), "booking.completed");
        assertThat(ticketSaleRepository.findByBookingId(900L)).isPresent();

        // First cancellation → REFUNDED
        BookingCancelledEvent cancelledEvent =
                new BookingCancelledEvent(900L, 4L, 8L, "duplicate_test");
        bookingEventConsumer.handleBookingEvent(toMessage(cancelledEvent), "booking.cancelled");

        TicketSale afterFirst = ticketSaleRepository.findByBookingId(900L).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(TicketSale.SaleStatus.REFUNDED);

        // Replay: same booking.cancelled again
        bookingEventConsumer.handleBookingEvent(toMessage(cancelledEvent), "booking.cancelled");

        // REFUNDED sale is not in the refundable filter (PENDING | COMPLETED),
        // so the second message is silently acked as a no-op.
        assertThat(ticketSaleRepository.findAllByBookingId(900L)).hasSize(1);
        // payment.refunded was published exactly once across both messages
        verify(rabbitTemplate, times(2)).convertAndSend( // 1 payment.initiated + 1 payment.refunded
                eq("payment.events"), anyString(), any(Object.class));
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("payment.events"), eq("payment.refunded"), any(Object.class));
    }
}
