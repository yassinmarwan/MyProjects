package com.team22.eventticketing.sales.saga;

import tools.jackson.databind.ObjectMapper;
import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.messaging.consumers.BookingEventConsumer;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.sales.service.SalesService;
import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Saga integration tests — Scenarios A / B / C.
 *
 * Uses a real PostgreSQL container for the TicketSale repository (JSONB support).
 * All external dependencies (RabbitMQ, MongoDB, Redis, Feign clients) are mocked.
 *
 * Scenario A — Happy path:
 *   booking.completed → PENDING TicketSale created + payment.initiated published
 *   → POST /api/sales/booking/{id} (payment succeeds) → COMPLETED + payment.completed published
 *
 * Scenario B — Payment failure:
 *   booking.completed → PENDING TicketSale created
 *   → POST /api/sales/booking/{id} with simulateFailure → FAILED + payment.failed published
 *
 * Scenario C — Booking cancellation:
 *   booking.completed → PENDING TicketSale created
 *   → booking.cancelled → REFUNDED + payment.refunded published
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("saga-test")
@Testcontainers
class SagaIntegrationTest {

    // ── Real PostgreSQL via Testcontainers (JSONB support required) ────────────
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-sales")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Mocked external dependencies ──────────────────────────────────────────

    // RedisConfig needs RedisConnectionFactory to create RedisTemplate + RedisCacheManager.
    // With RedisAutoConfiguration excluded, we provide a mock so those @Bean methods can run.
    // CacheEvictionService wraps all Redis calls in try-catch, so null returns are silently swallowed.
    @MockitoBean
    RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @MockitoBean
    BookingServiceClient bookingServiceClient;

    @MockitoBean
    UserServiceClient userServiceClient;

    @MockitoBean
    EventServiceClient eventServiceClient;

    // MongoEventLogger depends on MongoTemplate — mongo auto-config is excluded,
    // so we mock the logger directly to satisfy the observer registration.
    @MockitoBean
    com.team22.eventticketing.sales.observer.MongoEventLogger auditLogger;

    @MockitoBean
    com.team22.eventticketing.sales.repository.PaymentAuditEventRepository paymentAuditEventRepository;

    // ── Beans under test ──────────────────────────────────────────────────────
    @Autowired
    BookingEventConsumer bookingEventConsumer;

    @Autowired
    SalesService salesService;

    @Autowired
    TicketSaleRepository ticketSaleRepository;

    @Autowired
    ObjectMapper objectMapper;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Message toMessage(Object event) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(event);
        return new Message(body, new MessageProperties());
    }

    @BeforeEach
    void cleanDatabase() {
        ticketSaleRepository.deleteAll();
        reset(rabbitTemplate, bookingServiceClient);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Scenario A — Happy path
    // booking.completed → PENDING sale + payment.initiated
    // → S5-F4 payment success → COMPLETED + payment.completed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario A — Happy path: booking.completed → PENDING → payment processed → COMPLETED")
    void scenarioA_happyPath() throws Exception {

        // ── Step 1: consume booking.completed ────────────────────────────────
        BookingCompletedEvent completedEvent =
                new BookingCompletedEvent(100L, 1L, 5L, BigDecimal.valueOf(500.00));

        bookingEventConsumer.handleBookingEvent(
                toMessage(completedEvent), "booking.completed");

        // Assert: PENDING TicketSale created in sales-postgres
        Optional<TicketSale> maybeSale = ticketSaleRepository.findByBookingId(100L);
        assertThat(maybeSale).isPresent();
        TicketSale sale = maybeSale.get();
        assertThat(sale.getStatus()).isEqualTo(TicketSale.SaleStatus.PENDING);
        assertThat(sale.getUserId()).isEqualTo(1L);
        assertThat(sale.getAmount()).isEqualTo(500.00);

        // Assert: payment.initiated published to payment.events exchange
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"),
                eq("payment.initiated"),
                any(Object.class));

        // ── Step 2: S5-F4 — booking-service confirms PAYMENT_PENDING ─────────
        BookingDTO bookingDto = new BookingDTO(100L, 1L, 5L, "PAYMENT_PENDING",
                BigDecimal.valueOf(500.00));
        when(bookingServiceClient.getBooking(100L)).thenReturn(bookingDto);

        TicketSale processed = salesService.processBookingPayment(
                100L, TicketSale.PaymentMethod.CREDIT_CARD, "4242");

        // Assert: sale is now COMPLETED
        assertThat(processed.getStatus()).isEqualTo(TicketSale.SaleStatus.COMPLETED);
        assertThat(processed.getMethod()).isEqualTo(TicketSale.PaymentMethod.CREDIT_CARD);
        assertThat(processed.getTransactionDetails()).containsKey("cardLastFour");
        assertThat(processed.getTransactionDetails()).containsEntry("gatewayResponse", "approved");

        // Assert: payment.completed published
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"),
                eq("payment.completed"),
                any(Object.class));

        // Assert persisted state
        TicketSale persisted = ticketSaleRepository.findById(processed.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TicketSale.SaleStatus.COMPLETED);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Scenario B — Payment failure / compensation
    // booking.completed → PENDING sale + payment.initiated
    // → S5-F4 payment fails → FAILED + payment.failed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario B — Payment failure: booking.completed → PENDING → payment declined → FAILED")
    void scenarioB_paymentFailure() throws Exception {

        // ── Step 1: consume booking.completed ────────────────────────────────
        BookingCompletedEvent completedEvent =
                new BookingCompletedEvent(200L, 2L, 6L, BigDecimal.valueOf(300.00));

        bookingEventConsumer.handleBookingEvent(
                toMessage(completedEvent), "booking.completed");

        Optional<TicketSale> maybeSale = ticketSaleRepository.findByBookingId(200L);
        assertThat(maybeSale).isPresent();
        assertThat(maybeSale.get().getStatus()).isEqualTo(TicketSale.SaleStatus.PENDING);

        // payment.initiated published
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"),
                eq("payment.initiated"),
                any(Object.class));

        // ── Step 2: S5-F4 — payment gateway declines (simulateFailure=true) ──
        BookingDTO bookingDto = new BookingDTO(200L, 2L, 6L, "PAYMENT_PENDING",
                BigDecimal.valueOf(300.00));
        when(bookingServiceClient.getBooking(200L)).thenReturn(bookingDto);

        TicketSale failed = salesService.processBookingPayment(
                200L, TicketSale.PaymentMethod.DEBIT_CARD, null, true);

        // Assert: sale is FAILED
        assertThat(failed.getStatus()).isEqualTo(TicketSale.SaleStatus.FAILED);
        assertThat(failed.getTransactionDetails()).containsEntry("gatewayResponse", "declined");

        // Assert: payment.failed published (NOT payment.completed)
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"),
                eq("payment.failed"),
                any(Object.class));
        verify(rabbitTemplate, never()).convertAndSend(
                eq("payment.events"),
                eq("payment.completed"),
                any(Object.class));

        // Assert persisted state
        TicketSale persisted = ticketSaleRepository.findById(failed.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TicketSale.SaleStatus.FAILED);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Scenario C — Booking cancellation / refund
    // booking.completed → PENDING sale + payment.initiated
    // → booking.cancelled → REFUNDED + payment.refunded
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario C — Cancellation: booking.completed → PENDING → booking.cancelled → REFUNDED")
    void scenarioC_bookingCancellation() throws Exception {

        // ── Step 1: consume booking.completed ────────────────────────────────
        BookingCompletedEvent completedEvent =
                new BookingCompletedEvent(300L, 3L, 7L, BigDecimal.valueOf(750.00));

        bookingEventConsumer.handleBookingEvent(
                toMessage(completedEvent), "booking.completed");

        Optional<TicketSale> maybeSale = ticketSaleRepository.findByBookingId(300L);
        assertThat(maybeSale).isPresent();
        TicketSale pendingSale = maybeSale.get();
        assertThat(pendingSale.getStatus()).isEqualTo(TicketSale.SaleStatus.PENDING);

        // payment.initiated published on saga forward path
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"),
                eq("payment.initiated"),
                any(Object.class));

        // ── Step 2: consume booking.cancelled (saga compensation) ─────────────
        BookingCancelledEvent cancelledEvent =
                new BookingCancelledEvent(300L, 3L, 7L, "customer_request");

        bookingEventConsumer.handleBookingEvent(
                toMessage(cancelledEvent), "booking.cancelled");

        // Assert: sale is now REFUNDED
        TicketSale refunded = ticketSaleRepository.findById(pendingSale.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(TicketSale.SaleStatus.REFUNDED);
        assertThat(refunded.getTransactionDetails()).containsEntry("refundAmount", 750.00);
        assertThat(refunded.getTransactionDetails()).containsEntry("refundReason", "customer_request");

        // Assert: payment.refunded published
        verify(rabbitTemplate).convertAndSend(
                eq("payment.events"),
                eq("payment.refunded"),
                any(Object.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Consumer idempotency — duplicate booking.completed does not create second sale")
    void idempotency_duplicateBookingCompleted() throws Exception {
        BookingCompletedEvent event =
                new BookingCompletedEvent(400L, 4L, 8L, BigDecimal.valueOf(200.00));
        Message message = toMessage(event);

        bookingEventConsumer.handleBookingEvent(message, "booking.completed");
        bookingEventConsumer.handleBookingEvent(message, "booking.completed"); // replay

        long count = ticketSaleRepository.findAllByBookingId(400L).size();
        assertThat(count).isEqualTo(1); // only one sale, not two
    }

    @Test
    @DisplayName("S5-F4 rejects booking not in PAYMENT_PENDING status")
    void scenarioA_wrongBookingStatus_rejects() throws Exception {
        // Create a PENDING sale first
        BookingCompletedEvent event =
                new BookingCompletedEvent(500L, 5L, 9L, BigDecimal.valueOf(400.00));
        bookingEventConsumer.handleBookingEvent(toMessage(event), "booking.completed");

        // Booking service returns CONFIRMED (not PAYMENT_PENDING)
        BookingDTO bookingDto = new BookingDTO(500L, 5L, 9L, "CONFIRMED",
                BigDecimal.valueOf(400.00));
        when(bookingServiceClient.getBooking(500L)).thenReturn(bookingDto);

        assertThatThrownBy(() ->
                salesService.processBookingPayment(500L, TicketSale.PaymentMethod.WALLET, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("CONFIRMED");
    }
}