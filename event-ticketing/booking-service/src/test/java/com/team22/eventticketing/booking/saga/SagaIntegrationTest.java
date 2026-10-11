package com.team22.eventticketing.booking.saga;

import com.team22.eventticketing.booking.messaging.BookingEventPublisher;
import com.team22.eventticketing.booking.messaging.BookingSagaListener;
import com.team22.eventticketing.booking.model.Booking;
import com.team22.eventticketing.booking.model.BookingStatus;
import com.team22.eventticketing.booking.observer.MongoEventLogger;
import com.team22.eventticketing.booking.repository.BookingRepository;
import com.team22.eventticketing.booking.repository.EventNodeRepository;
import com.team22.eventticketing.booking.repository.UserNodeRecommendationRepository;
import com.team22.eventticketing.booking.repository.UserNodeRepository;
import com.team22.eventticketing.booking.service.BookingService;
import com.team22.eventticketing.contracts.dto.EventDTO;
import com.team22.eventticketing.contracts.events.PaymentFailedEvent;
import com.team22.eventticketing.contracts.events.TicketIssuedEvent;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.feign.TicketServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Saga integration tests — real PostgreSQL (Testcontainers), mocked RabbitMQ + all other external deps.
 *
 * Scenario A — S3-F4: CHECKED_IN booking → COMPLETED + booking.completed published
 * Scenario B — Saga compensation: PaymentFailedEvent → booking CANCELLED + booking.cancelled published
 * Scenario C — Saga success: TicketIssuedEvent → booking COMPLETED
 * Scenario D — S3-F2: PENDING/CONFIRMED booking → CANCELLED + booking.cancelled published
 * Scenario E — S3-F7: PENDING booking + UPCOMING event → CONFIRMED
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("saga-test")
@Testcontainers
@SuppressWarnings("resource")
class SagaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-bookings")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Mocked external dependencies ──────────────────────────────────────────────
    @MockitoBean RedisConnectionFactory redisConnectionFactory;
    @MockitoBean RabbitTemplate rabbitTemplate;
    @MockitoBean EventServiceClient eventServiceClient;
    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean TicketServiceClient ticketServiceClient;
    @MockitoBean MongoEventLogger mongoEventLogger;
    @MockitoBean Driver neo4jDriver;
    @MockitoBean UserNodeRepository userNodeRepository;
    @MockitoBean EventNodeRepository eventNodeRepository;
    @MockitoBean UserNodeRecommendationRepository userNodeRecommendationRepository;

    // ── Beans under test ──────────────────────────────────────────────────────────
    @Autowired BookingService bookingService;
    @Autowired BookingSagaListener bookingSagaListener;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingEventPublisher bookingEventPublisher;

    @BeforeEach
    void clean() {
        bookingRepository.deleteAll();
        reset(rabbitTemplate, eventServiceClient);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private Booking savedBooking(BookingStatus status) {
        Booking b = new Booking();
        b.setUserId(1L);
        b.setEventId(5L);
        b.setContactEmail("test@example.com");
        b.setStatus(status);
        b.setTotalAmount(500.0);
        b.setBookingDate(LocalDateTime.now());
        return bookingRepository.save(b);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Scenario A — S3-F4: CHECKED_IN → COMPLETED + booking.completed
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario A — S3-F4: CHECKED_IN booking → COMPLETED + booking.completed published")
    void scenarioA_completeBooking_checkedInBecomesCompleted() {
        Booking booking = savedBooking(BookingStatus.CHECKED_IN);

        Booking result = bookingService.completeBooking(booking.getId());

        assertThat(result.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(result.getUserId()).isEqualTo(1L);

        verify(rabbitTemplate).convertAndSend(
                eq("booking.events"), eq("booking.completed"), any(Object.class));

        Booking persisted = bookingRepository.findById(result.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("Scenario A — S3-F4 reject: non-CHECKED_IN booking → 400 and no publish")
    void scenarioA_completeBooking_nonCheckedIn_rejects() {
        Booking booking = savedBooking(BookingStatus.PENDING);

        assertThatThrownBy(() -> bookingService.completeBooking(booking.getId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("CHECKED_IN");

        verify(rabbitTemplate, never()).convertAndSend(
                eq("booking.events"), eq("booking.completed"), any(Object.class));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Scenario B — Saga compensation: PaymentFailedEvent → CANCELLED + booking.cancelled
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario B — PaymentFailed: booking transitions to CANCELLED + booking.cancelled published")
    void scenarioB_paymentFailed_bookingCancelled() {
        Booking booking = savedBooking(BookingStatus.CONFIRMED);

        bookingSagaListener.handlePaymentFailed(
                new PaymentFailedEvent(10L, booking.getId(), "card declined"));

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        verify(rabbitTemplate).convertAndSend(
                eq("booking.events"), eq("booking.cancelled"), any(Object.class));
    }

    @Test
    @DisplayName("Scenario B — PaymentFailed on missing booking → no DB change, no publish")
    void scenarioB_paymentFailed_notFound_noOp() {
        bookingSagaListener.handlePaymentFailed(
                new PaymentFailedEvent(10L, 99999L, "ghost booking"));

        verify(rabbitTemplate, never()).convertAndSend(
                eq("booking.events"), eq("booking.cancelled"), any(Object.class));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Scenario C — Saga success: TicketIssuedEvent → COMPLETED
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario C — TicketIssued: booking transitions to COMPLETED")
    void scenarioC_ticketIssued_bookingCompleted() {
        Booking booking = savedBooking(BookingStatus.CONFIRMED);

        bookingSagaListener.handleTicketIssued(
                new TicketIssuedEvent(20L, booking.getId(), "Alice", "TKT-001"));

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.COMPLETED);

        // TicketIssued does NOT publish any downstream event
        verify(rabbitTemplate, never()).convertAndSend(
                eq("booking.events"), anyString(), any(Object.class));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Scenario D — S3-F2: cancelBooking
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario D — S3-F2: PENDING booking → CANCELLED + booking.cancelled published")
    void scenarioD_cancelBooking_pendingCancelled() {
        Booking booking = savedBooking(BookingStatus.PENDING);

        bookingService.cancelBooking(booking.getId());

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        verify(rabbitTemplate).convertAndSend(
                eq("booking.events"), eq("booking.cancelled"), any(Object.class));
    }

    @Test
    @DisplayName("Scenario D — S3-F2: CONFIRMED booking → CANCELLED + booking.cancelled published")
    void scenarioD_cancelBooking_confirmedCancelled() {
        Booking booking = savedBooking(BookingStatus.CONFIRMED);

        bookingService.cancelBooking(booking.getId());

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(rabbitTemplate).convertAndSend(
                eq("booking.events"), eq("booking.cancelled"), any(Object.class));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Scenario E — S3-F7: confirmBooking
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Scenario E — S3-F7: PENDING booking + UPCOMING event → CONFIRMED")
    void scenarioE_confirmBooking_pendingAndUpcomingEvent() {
        Booking booking = savedBooking(BookingStatus.PENDING);
        when(eventServiceClient.getEvent(5L)).thenReturn(
                new EventDTO(5L, "Test Event", "UPCOMING", LocalDateTime.now().plusDays(10)));

        Booking result = bookingService.confirmBooking(booking.getId(), 5L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        Booking persisted = bookingRepository.findById(result.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(persisted.getEventId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Scenario E — S3-F7 reject: event not UPCOMING → 400 and booking stays PENDING")
    void scenarioE_confirmBooking_eventNotUpcoming_rejects() {
        Booking booking = savedBooking(BookingStatus.PENDING);
        when(eventServiceClient.getEvent(5L)).thenReturn(
                new EventDTO(5L, "Test Event", "CANCELLED", LocalDateTime.now().plusDays(10)));

        assertThatThrownBy(() -> bookingService.confirmBooking(booking.getId(), 5L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("not UPCOMING");

        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.PENDING);
    }
}
