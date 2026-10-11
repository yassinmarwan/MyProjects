package com.team22.eventticketing.ticket.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.events.BookingPlacedEvent;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.feign.TicketServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import com.team22.eventticketing.ticket.model.Ticket;
import com.team22.eventticketing.ticket.model.TicketStatus;
import com.team22.eventticketing.ticket.observer.MongoEventLogger;
import com.team22.eventticketing.ticket.repository.BookingEventMappingRepository;
import com.team22.eventticketing.ticket.repository.TicketRepository;
import com.team22.eventticketing.ticket.repository.TicketScanEventRepository;
import com.team22.eventticketing.ticket.service.CacheEvictionService;
import com.team22.eventticketing.ticket.service.TicketScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * RabbitMQ saga consumer integration tests — real RabbitMQ + real PostgreSQL via Testcontainers.
 *
 * Publishes booking.* messages onto a live broker and waits for {@link BookingSagaListener}
 * to mutate ticket-postgres, then asserts both the persisted state and the downstream
 * publish calls via a spy on TicketEventPublisher.
 *
 * Mongo, Redis and Cassandra autoconfigs are excluded by the consumer-it profile;
 * their beans are replaced by @MockitoBean. Feign clients are mocked too — the saga
 * consumer does not call them directly so no stubbing is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("consumer-it")
@Testcontainers
@Import(BookingSagaListenerIT.TestBeans.class)
@SuppressWarnings("resource") // containers closed by the @Testcontainers JUnit 5 extension
class BookingSagaListenerIT {

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-tickets-it")
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

    // ── Mocked infra-bound beans (Mongo/Redis/Cassandra excluded by profile) ──

    @MockitoBean RedisConnectionFactory redisConnectionFactory;
    @MockitoBean @SuppressWarnings("rawtypes") RedisTemplate redisTemplate;
    @MockitoBean CacheEvictionService cacheEvictionService;
    @MockitoBean MongoEventLogger mongoEventLogger;
    @MockitoBean TicketScanEventRepository ticketScanEventRepository;
    @MockitoBean TicketScanService ticketScanService;

    // Feign clients — saga listener does not invoke them, but Spring needs the beans
    @MockitoBean BookingServiceClient bookingServiceClient;
    @MockitoBean EventServiceClient eventServiceClient;
    @MockitoBean TicketServiceClient ticketServiceClient;
    @MockitoBean UserServiceClient userServiceClient;

    // Spy wraps the real publisher so we can verify call args while the real
    // RabbitTemplate.convertAndSend still executes.
    @MockitoSpyBean TicketEventPublisher ticketEventPublisher;

    // ── Beans under test ──────────────────────────────────────────────────

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired TicketRepository ticketRepository;
    @Autowired BookingEventMappingRepository mappingRepository;

    @BeforeEach
    void cleanDatabase() {
        ticketRepository.deleteAll();
        mappingRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // booking.placed → BookingEventMapping row persisted (Strategy A)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Publish booking.placed → mapping (bookingId → eventId) persisted in DB")
    void publishBookingPlaced_mappingPersistedInDB() {
        BookingPlacedEvent event = new BookingPlacedEvent(2001L, 10L, 500L);

        rabbitTemplate.convertAndSend("booking.events", "booking.placed", event);

        await().atMost(10, SECONDS)
                .until(() -> mappingRepository.findById(2001L).isPresent());

        assertThat(mappingRepository.findById(2001L).orElseThrow().getEventId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Duplicate booking.placed messages are idempotent — single mapping row")
    void publishBookingPlaced_twice_singleMappingRow() {
        BookingPlacedEvent event = new BookingPlacedEvent(2002L, 10L, 500L);

        rabbitTemplate.convertAndSend("booking.events", "booking.placed", event);
        await().atMost(10, SECONDS)
                .until(() -> mappingRepository.findById(2002L).isPresent());

        rabbitTemplate.convertAndSend("booking.events", "booking.placed", event);

        await().during(2, SECONDS).atMost(5, SECONDS)
                .until(() -> mappingRepository.count() >= 1);

        assertThat(mappingRepository.findById(2002L)).isPresent();
        assertThat(mappingRepository.count()).isEqualTo(1L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // booking.cancelled → VALID tickets transition to CANCELLED + ticket.cancelled emitted
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Publish booking.cancelled → VALID tickets become CANCELLED in DB; ticket.cancelled emitted per ticket")
    void publishBookingCancelled_validTicketsCancelledInDB() {
        Long bookingId = 3001L;

        // Seed: 2 VALID tickets + 1 USED ticket for this booking
        Ticket t1 = newTicket(bookingId, 600L, "TKT-A", TicketStatus.VALID);
        Ticket t2 = newTicket(bookingId, 600L, "TKT-B", TicketStatus.VALID);
        Ticket t3 = newTicket(bookingId, 600L, "TKT-C", TicketStatus.USED);
        ticketRepository.saveAll(List.of(t1, t2, t3));

        BookingCancelledEvent event = new BookingCancelledEvent(bookingId, 10L, 600L, "user_requested");
        rabbitTemplate.convertAndSend("booking.events", "booking.cancelled", event);

        await().atMost(10, SECONDS).until(() -> {
            List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
            long cancelled = tickets.stream()
                    .filter(t -> t.getStatus() == TicketStatus.CANCELLED).count();
            return cancelled == 2L;
        });

        List<Ticket> after = ticketRepository.findByBookingId(bookingId);
        assertThat(after).hasSize(3);
        long cancelledCount = after.stream().filter(t -> t.getStatus() == TicketStatus.CANCELLED).count();
        long usedCount = after.stream().filter(t -> t.getStatus() == TicketStatus.USED).count();
        assertThat(cancelledCount).isEqualTo(2L); // only the VALID ones flipped
        assertThat(usedCount).isEqualTo(1L);      // USED ticket untouched

        verify(ticketEventPublisher, atLeast(2)).publishTicketCancelled(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // booking.completed → no DB mutation; one ticket.status-changed published per ticket
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Publish booking.completed → no ticket-status mutation; ticket.status-changed emitted per ticket")
    void publishBookingCompleted_noMutation_emitsStatusChangedPerTicket() {
        Long bookingId = 4001L;

        // Seed: 2 tickets for this booking
        Ticket t1 = newTicket(bookingId, 700L, "TKT-X", TicketStatus.VALID);
        Ticket t2 = newTicket(bookingId, 700L, "TKT-Y", TicketStatus.USED);
        ticketRepository.saveAll(List.of(t1, t2));

        BookingCompletedEvent event = new BookingCompletedEvent(
                bookingId, 10L, 700L, java.math.BigDecimal.valueOf(250.00));
        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);

        // Wait until 2 status-changed events have been published
        await().atMost(10, SECONDS)
                .untilAsserted(() -> verify(ticketEventPublisher, atLeast(2))
                        .publishTicketStatusChanged(any()));

        // Assert DB was NOT mutated — statuses unchanged
        List<Ticket> after = ticketRepository.findByBookingId(bookingId);
        assertThat(after).hasSize(2);
        long valid = after.stream().filter(t -> t.getStatus() == TicketStatus.VALID).count();
        long used = after.stream().filter(t -> t.getStatus() == TicketStatus.USED).count();
        assertThat(valid).isEqualTo(1L);
        assertThat(used).isEqualTo(1L);
    }

    // ── helpers ──

    private Ticket newTicket(Long bookingId, Long eventId, String code, TicketStatus status) {
        Ticket t = new Ticket();
        t.setBookingId(bookingId);
        t.setEventId(eventId);
        t.setAttendeeName("Test " + code);
        t.setTicketCode(code);
        t.setStatus(status);
        t.setIssuedAt(LocalDateTime.now());
        return t;
    }
}
