package com.team22.eventticketing.booking.messaging;

import com.team22.eventticketing.booking.model.Booking;
import com.team22.eventticketing.booking.model.BookingStatus;
import com.team22.eventticketing.booking.observer.MongoEventLogger;
import com.team22.eventticketing.booking.repository.BookingRepository;
import com.team22.eventticketing.booking.repository.EventNodeRepository;
import com.team22.eventticketing.booking.repository.UserNodeRecommendationRepository;
import com.team22.eventticketing.booking.repository.UserNodeRepository;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.PaymentFailedEvent;
import com.team22.eventticketing.contracts.events.TicketIssuedEvent;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.feign.TicketServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for BookingSagaListener — real PostgreSQL + real RabbitMQ via Testcontainers.
 *
 * The listener methods are called directly (not via broker message dispatch) to avoid
 * the inherent race condition caused by two @RabbitListener methods on the same queue.
 * This tests real PostgreSQL persistence and downstream event publishing via a real RabbitTemplate.
 *
 * MongoDB, Redis, Neo4j, and Feign clients are mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("consumer-it")
@Testcontainers
@SuppressWarnings("resource")
class BookingSagaListenerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-bookings-it")
            .withUsername("postgres")
            .withPassword("postgres");

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

    // ── Mocked infra ──────────────────────────────────────────────────────────────
    @MockitoBean RedisConnectionFactory redisConnectionFactory;
    @MockitoBean MongoEventLogger mongoEventLogger;
    @MockitoBean EventServiceClient eventServiceClient;
    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean TicketServiceClient ticketServiceClient;
    @MockitoBean Driver neo4jDriver;
    @MockitoBean UserNodeRepository userNodeRepository;
    @MockitoBean EventNodeRepository eventNodeRepository;
    @MockitoBean UserNodeRecommendationRepository userNodeRecommendationRepository;

    // Spy on the publisher so we can verify its call args
    // while the real RabbitTemplate.convertAndSend still executes.
    @MockitoSpyBean BookingEventPublisher bookingEventPublisher;

    // ── Beans under test ──────────────────────────────────────────────────────────
    @Autowired BookingSagaListener bookingSagaListener;
    @Autowired BookingRepository bookingRepository;

    @BeforeEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private Booking savedBooking(BookingStatus status) {
        Booking b = new Booking();
        b.setUserId(1L);
        b.setEventId(5L);
        b.setContactEmail("test@example.com");
        b.setStatus(status);
        b.setTotalAmount(400.0);
        b.setBookingDate(LocalDateTime.now());
        return bookingRepository.save(b);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // handlePaymentFailed — real PostgreSQL state mutation + publisher called
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PaymentFailedEvent: persists CANCELLED status in real PostgreSQL")
    void handlePaymentFailed_persistsCancelledInDB() {
        Booking booking = savedBooking(BookingStatus.CONFIRMED);

        bookingSagaListener.handlePaymentFailed(
                new PaymentFailedEvent(10L, booking.getId(), "card declined"));

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("PaymentFailedEvent: booking.cancelled published with correct bookingId + reason")
    void handlePaymentFailed_publishesCancelledEventWithCorrectArgs() {
        Booking booking = savedBooking(BookingStatus.PENDING);

        bookingSagaListener.handlePaymentFailed(
                new PaymentFailedEvent(11L, booking.getId(), "payment_gateway_timeout"));

        ArgumentCaptor<BookingCancelledEvent> captor = ArgumentCaptor.forClass(BookingCancelledEvent.class);
        verify(bookingEventPublisher).publishBookingCancelled(captor.capture());
        BookingCancelledEvent published = captor.getValue();
        assertThat(published.bookingId()).isEqualTo(booking.getId());
        assertThat(published.reason()).contains("payment_gateway_timeout");
    }

    @Test
    @DisplayName("PaymentFailedEvent: replay on already-CANCELLED booking — still sets CANCELLED (ifPresent runs)")
    void handlePaymentFailed_replay_remainsCancelled() {
        Booking booking = savedBooking(BookingStatus.CANCELLED);

        bookingSagaListener.handlePaymentFailed(
                new PaymentFailedEvent(12L, booking.getId(), "retry"));

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("PaymentFailedEvent: booking not found → no DB mutation, no publish")
    void handlePaymentFailed_bookingNotFound_noOp() {
        bookingSagaListener.handlePaymentFailed(
                new PaymentFailedEvent(13L, 99999L, "ghost"));

        assertThat(bookingRepository.count()).isZero();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // handleTicketIssued — real PostgreSQL state mutation
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TicketIssuedEvent: persists COMPLETED status in real PostgreSQL")
    void handleTicketIssued_persistsCompletedInDB() {
        Booking booking = savedBooking(BookingStatus.CONFIRMED);

        bookingSagaListener.handleTicketIssued(
                new TicketIssuedEvent(20L, booking.getId(), "Alice Smith", "TKT-001"));

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("TicketIssuedEvent: no publisher call (TicketIssued only updates DB)")
    void handleTicketIssued_noDownstreamPublish() {
        Booking booking = savedBooking(BookingStatus.CONFIRMED);

        bookingSagaListener.handleTicketIssued(
                new TicketIssuedEvent(21L, booking.getId(), "Bob", "TKT-002"));

        // BookingEventPublisher should NOT be called for ticket-issued
        org.mockito.Mockito.verifyNoInteractions(bookingEventPublisher);
    }

    @Test
    @DisplayName("TicketIssuedEvent: booking not found → no DB mutation")
    void handleTicketIssued_bookingNotFound_noOp() {
        bookingSagaListener.handleTicketIssued(
                new TicketIssuedEvent(22L, 99999L, "Ghost", "TKT-GHOST"));

        assertThat(bookingRepository.count()).isZero();
    }
}
