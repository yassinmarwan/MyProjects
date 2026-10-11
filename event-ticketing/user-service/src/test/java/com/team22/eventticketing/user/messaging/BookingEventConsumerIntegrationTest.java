package com.team22.eventticketing.user.messaging;

import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.observer.MongoEventLogger;
import com.team22.eventticketing.user.repository.AuthEventRepository;
import com.team22.eventticketing.user.repository.UserRepository;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * RabbitMQ consumer integration tests — real RabbitMQ + real PostgreSQL via Testcontainers.
 *
 * Publishes messages onto the live broker and waits for the consumer to mutate the
 * user preferences in the DB, then asserts persisted state.
 *
 * MongoDB and Redis are excluded; their beans are replaced by @MockitoBean.
 * Feign clients are mocked so the context can load without real service URLs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("consumer-it")
@Testcontainers
@SuppressWarnings("resource") // containers closed by @Testcontainers JUnit 5 extension
class BookingEventConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-users-it")
            .withUsername("postgres")
            .withPassword("postgres");

    // Default RabbitMQ image uses guest/guest; no withUser() needed.
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
    @MockitoBean MongoEventLogger mongoEventLogger;
    @MockitoBean AuthEventRepository authEventRepository;
    @MockitoBean BookingServiceClient bookingServiceClient;

    // ── Beans under test ──────────────────────────────────────────────────

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private User saveTestUser(Long phoneSuffix) {
        User u = new User("Test User", "test" + phoneSuffix + "@example.com",
                "hashed", "010000" + String.format("%05d", phoneSuffix), Role.ATTENDEE);
        u.setStatus(UserStatus.ACTIVE);
        u.setPreferences(new HashMap<>());
        return userRepository.save(u);
    }

    // ══════════════════════════════════════════════════════════════════════
    // booking.completed → user preferences updated with totalBookings + totalSpent
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Publish booking.completed → totalBookings and totalSpent updated in DB")
    void publishBookingCompleted_updatesUserPreferences() {
        User saved = saveTestUser(1L);
        Long userId = saved.getId();

        BookingCompletedEvent event =
                new BookingCompletedEvent(1001L, userId, 5L, BigDecimal.valueOf(200.0));
        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);

        await().atMost(10, SECONDS)
                .until(() -> {
                    User u = userRepository.findById(userId).orElse(null);
                    return u != null && u.getPreferences().containsKey("processed:booking.completed:1001");
                });

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(((Number) updated.getPreferences().get("totalBookings")).longValue()).isEqualTo(1L);
        assertThat(((Number) updated.getPreferences().get("totalSpent")).doubleValue()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("Duplicate booking.completed messages are idempotent — totalBookings counted once")
    void publishBookingCompleted_twice_idempotentSingleCount() {
        User saved = saveTestUser(2L);
        Long userId = saved.getId();

        BookingCompletedEvent event =
                new BookingCompletedEvent(1002L, userId, 5L, BigDecimal.valueOf(100.0));

        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);
        await().atMost(10, SECONDS)
                .until(() -> {
                    User u = userRepository.findById(userId).orElse(null);
                    return u != null && u.getPreferences().containsKey("processed:booking.completed:1002");
                });

        // Replay the same event
        rabbitTemplate.convertAndSend("booking.events", "booking.completed", event);

        // Allow time for potential second processing, then assert count is still 1
        await().during(2, SECONDS).atMost(8, SECONDS)
                .until(() -> {
                    User u = userRepository.findById(userId).orElse(null);
                    return u != null && ((Number) u.getPreferences()
                            .getOrDefault("totalBookings", 0)).longValue() == 1L;
                });

        User final_ = userRepository.findById(userId).orElseThrow();
        assertThat(((Number) final_.getPreferences().get("totalBookings")).longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Publish booking.cancelled after booking.completed → totalBookings decremented")
    void publishBookingCancelled_decrementsTotalBookings() {
        User saved = saveTestUser(3L);
        Long userId = saved.getId();

        // First: complete a booking to increment totalBookings to 1
        BookingCompletedEvent completed =
                new BookingCompletedEvent(1003L, userId, 5L, BigDecimal.valueOf(150.0));
        rabbitTemplate.convertAndSend("booking.events", "booking.completed", completed);
        await().atMost(10, SECONDS)
                .until(() -> {
                    User u = userRepository.findById(userId).orElse(null);
                    return u != null && u.getPreferences().containsKey("processed:booking.completed:1003");
                });

        // Then: cancel that booking
        BookingCancelledEvent cancelled =
                new BookingCancelledEvent(1003L, userId, 5L, "customer_request");
        rabbitTemplate.convertAndSend("booking.events", "booking.cancelled", cancelled);

        await().atMost(10, SECONDS)
                .until(() -> {
                    User u = userRepository.findById(userId).orElse(null);
                    return u != null && u.getPreferences().containsKey("processed:booking.cancelled:1003");
                });

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(((Number) updated.getPreferences().get("totalBookings")).longValue()).isEqualTo(0L);
    }

    @Test
    @DisplayName("booking.placed routing key → consumer silently acks, no user preferences changed")
    void bookingPlaced_unknownRoutingKey_noPreferenceChange() throws InterruptedException {
        User saved = saveTestUser(4L);
        Long userId = saved.getId();

        BookingCompletedEvent event =
                new BookingCompletedEvent(1004L, userId, 5L, BigDecimal.TEN);
        rabbitTemplate.convertAndSend("booking.events", "booking.placed", event);

        // Wait briefly and assert user preferences were not modified
        Thread.sleep(2000);

        User notUpdated = userRepository.findById(userId).orElseThrow();
        assertThat(notUpdated.getPreferences()).doesNotContainKey("totalBookings");
    }
}
