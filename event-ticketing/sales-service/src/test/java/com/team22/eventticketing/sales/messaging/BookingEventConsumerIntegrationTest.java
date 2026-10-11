package com.team22.eventticketing.sales.messaging;

import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.messaging.publishers.PaymentEventPublisher;
import com.team22.eventticketing.sales.observer.MongoEventLogger;
import com.team22.eventticketing.sales.repository.PaymentAuditEventRepository;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

/**
 * RabbitMQ consumer integration tests — real RabbitMQ + real PostgreSQL via Testcontainers.
 *
 * Publishes messages onto the live broker and waits for the consumer to mutate the DB,
 * then asserts both the persisted state and the downstream publish calls via a spy.
 *
 * MongoDB and Redis are excluded; their beans are replaced by @MockitoBean.
 * Feign clients are mocked so the consumer can be loaded without real service URLs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("consumer-it")
@Testcontainers
@SuppressWarnings("resource") // containers closed by @Testcontainers JUnit 5 extension
class BookingEventConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-sales-it")
            .withUsername("postgres")
            .withPassword("postgres");

    // Default RabbitMQ image already uses guest/guest; no withUser() needed.
    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
    }

    // ── Mocked infra dependencies ─────────────────────────────────────────

    @MockitoBean RedisConnectionFactory redisConnectionFactory;
    @MockitoBean MongoEventLogger auditLogger;
    @MockitoBean PaymentAuditEventRepository paymentAuditEventRepository;
    @MockitoBean BookingServiceClient bookingServiceClient;
    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean EventServiceClient eventServiceClient;

    // Spy wraps the real publisher so we can verify its call args
    // while the real RabbitTemplate.convertAndSend still executes.
    @MockitoSpyBean PaymentEventPublisher paymentEventPublisher;

    // ── Beans under test ──────────────────────────────────────────────────

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired TicketSaleRepository ticketSaleRepository;

    @BeforeEach
    void cleanDatabase() {
        ticketSaleRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // booking.completed → consumer creates PENDING TicketSale + publishes payment.initiated
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Publish booking.completed → PENDING TicketSale created in DB")
    void publishBookingCompleted_ticketSaleCreatedInDB() {
        BookingCompletedEvent event =
                new BookingCompletedEvent(1001L, 1L, 5L, BigDecimal.valueOf(200.0));

        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);

        await().atMost(10, SECONDS)
                .until(() -> ticketSaleRepository.findByBookingId(1001L).isPresent());

        TicketSale sale = ticketSaleRepository.findByBookingId(1001L).orElseThrow();
        assertThat(sale.getStatus()).isEqualTo(TicketSale.SaleStatus.PENDING);
        assertThat(sale.getAmount()).isEqualTo(200.0);
        assertThat(sale.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Publish booking.completed → payment.initiated published with correct bookingId + amount")
    void publishBookingCompleted_paymentInitiatedEmittedWithCorrectArgs() {
        BookingCompletedEvent event =
                new BookingCompletedEvent(1002L, 1L, 5L, BigDecimal.valueOf(350.0));

        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);

        await().atMost(10, SECONDS)
                .until(() -> ticketSaleRepository.findByBookingId(1002L).isPresent());

        verify(paymentEventPublisher).publishPaymentInitiated(anyLong(), eq(1002L), eq(350.0));
    }

    @Test
    @DisplayName("Duplicate booking.completed messages are idempotent — single TicketSale in DB")
    void publishBookingCompleted_twice_onlyOneTicketSale() {
        BookingCompletedEvent event =
                new BookingCompletedEvent(1003L, 1L, 5L, BigDecimal.valueOf(100.0));

        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);
        await().atMost(10, SECONDS)
                .until(() -> ticketSaleRepository.findByBookingId(1003L).isPresent());

        // replay the same event
        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);

        // short wait to allow a potential second processing cycle
        await().during(2, SECONDS).atMost(5, SECONDS)
                .until(() -> ticketSaleRepository.findAllByBookingId(1003L).size() == 1);

        assertThat(ticketSaleRepository.findAllByBookingId(1003L)).hasSize(1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // booking.cancelled → consumer transitions to REFUNDED + publishes payment.refunded
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Publish booking.cancelled after PENDING sale → sale transitions to REFUNDED in DB")
    void publishBookingCancelled_pendingSaleRefundedInDB() {
        // First create a PENDING sale via booking.completed
        BookingCompletedEvent completed =
                new BookingCompletedEvent(1004L, 2L, 6L, BigDecimal.valueOf(500.0));
        rabbitTemplate.convertAndSend("booking.events", "booking.completed", completed);
        await().atMost(10, SECONDS)
                .until(() -> ticketSaleRepository.findByBookingId(1004L).isPresent());

        // Now cancel the booking
        BookingCancelledEvent cancelled =
                new BookingCancelledEvent(1004L, 2L, 6L, "customer_request");
        rabbitTemplate.convertAndSend("booking.events", "booking.cancelled", cancelled);

        await().atMost(10, SECONDS)
                .until(() -> ticketSaleRepository.findByBookingId(1004L)
                        .map(s -> s.getStatus() == TicketSale.SaleStatus.REFUNDED)
                        .orElse(false));

        TicketSale refunded = ticketSaleRepository.findByBookingId(1004L).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(TicketSale.SaleStatus.REFUNDED);
        assertThat(refunded.getTransactionDetails()).containsEntry("refundAmount", 500.0);
        assertThat(refunded.getTransactionDetails()).containsEntry("refundReason", "customer_request");
    }

    @Test
    @DisplayName("Publish booking.cancelled → payment.refunded published with correct refundAmount")
    void publishBookingCancelled_paymentRefundedEmitted() {
        BookingCompletedEvent completed =
                new BookingCompletedEvent(1005L, 2L, 6L, BigDecimal.valueOf(750.0));
        rabbitTemplate.convertAndSend("booking.events", "booking.completed", completed);
        await().atMost(10, SECONDS)
                .until(() -> ticketSaleRepository.findByBookingId(1005L).isPresent());

        Long saleId = ticketSaleRepository.findByBookingId(1005L).orElseThrow().getId();

        BookingCancelledEvent cancelled =
                new BookingCancelledEvent(1005L, 2L, 6L, "fraud");
        rabbitTemplate.convertAndSend("booking.events", "booking.cancelled", cancelled);

        await().atMost(10, SECONDS)
                .until(() -> ticketSaleRepository.findByBookingId(1005L)
                        .map(s -> s.getStatus() == TicketSale.SaleStatus.REFUNDED)
                        .orElse(false));

        verify(paymentEventPublisher).publishPaymentRefunded(eq(saleId), eq(1005L), eq(750.0));
    }

    @Test
    @DisplayName("booking.placed routing key → consumer silently acks, no TicketSale created")
    void bookingPlaced_unknownRoutingKey_noSaleCreated() throws InterruptedException {
        BookingCompletedEvent event =
                new BookingCompletedEvent(1006L, 1L, 5L, BigDecimal.TEN);

        rabbitTemplate.convertAndSend("booking.events", "booking.placed", event);

        // Wait briefly and assert nothing was created
        Thread.sleep(2000);
        assertThat(ticketSaleRepository.findByBookingId(1006L)).isEmpty();
    }
}
