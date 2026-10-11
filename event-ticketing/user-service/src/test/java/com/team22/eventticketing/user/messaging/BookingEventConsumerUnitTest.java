package com.team22.eventticketing.user.messaging;

import tools.jackson.databind.ObjectMapper;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.repository.UserRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingEventConsumerUnitTest {

    @Mock UserRepository userRepository;

    // Real ObjectMapper — event records have no special types needing modules
    ObjectMapper objectMapper = new ObjectMapper();

    BookingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingEventConsumer(userRepository, objectMapper);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Message toMessage(Object event) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(event);
        return new Message(body, new MessageProperties());
    }

    private User userWithPrefs(Long id, Map<String, Object> prefs) {
        User u = new User("Alice", "alice" + id + "@example.com", "hashed", "0101234567" + id, Role.ATTENDEE);
        u.setId(id);
        u.setStatus(UserStatus.ACTIVE);
        u.setPreferences(new HashMap<>(prefs));
        return u;
    }

    // ══════════════════════════════════════════════════════════════════════
    // booking.completed → increments totalBookings + totalSpent in user preferences
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("booking.completed: user found → totalBookings and totalSpent incremented, user saved")
    void bookingCompleted_userExists_incrementsPrefsAndSaves() throws Exception {
        User user = userWithPrefs(1L, Map.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingCompletedEvent event = new BookingCompletedEvent(100L, 1L, 5L, BigDecimal.valueOf(250.0));
        consumer.handleBookingEvent(toMessage(event), "booking.completed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(((Number) saved.getPreferences().get("totalBookings")).longValue()).isEqualTo(1L);
        assertThat(((Number) saved.getPreferences().get("totalSpent")).doubleValue()).isEqualTo(250.0);
        assertThat(saved.getPreferences()).containsKey("processed:booking.completed:100");
    }

    @Test
    @DisplayName("booking.completed: user not found → silent no-op, nothing saved")
    void bookingCompleted_userNotFound_silentNoOp() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        BookingCompletedEvent event = new BookingCompletedEvent(101L, 999L, 5L, BigDecimal.valueOf(100.0));
        consumer.handleBookingEvent(toMessage(event), "booking.completed");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("booking.completed: duplicate event replayed → idempotency key present, no second save")
    void bookingCompleted_duplicate_idempotentSkipsSave() throws Exception {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("totalBookings", 1L);
        prefs.put("totalSpent", 250.0);
        prefs.put("processed:booking.completed:100", true);
        User user = userWithPrefs(1L, prefs);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        BookingCompletedEvent event = new BookingCompletedEvent(100L, 1L, 5L, BigDecimal.valueOf(250.0));
        consumer.handleBookingEvent(toMessage(event), "booking.completed");

        verify(userRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // booking.cancelled → decrements totalBookings in user preferences
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("booking.cancelled: user has bookings → totalBookings decremented, idempotency key saved")
    void bookingCancelled_userExists_decrementsBookingsAndSaves() throws Exception {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("totalBookings", 2L);
        User user = userWithPrefs(2L, prefs);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingCancelledEvent event = new BookingCancelledEvent(200L, 2L, 6L, "customer_request");
        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(((Number) saved.getPreferences().get("totalBookings")).longValue()).isEqualTo(1L);
        assertThat(saved.getPreferences()).containsKey("processed:booking.cancelled:200");
    }

    @Test
    @DisplayName("booking.cancelled: user totalBookings already 0 → stays at 0 (never negative)")
    void bookingCancelled_zeroBookings_doesNotGoNegative() throws Exception {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("totalBookings", 0L);
        User user = userWithPrefs(2L, prefs);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingCancelledEvent event = new BookingCancelledEvent(201L, 2L, 6L, "test");
        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(((Number) captor.getValue().getPreferences().get("totalBookings")).longValue())
                .isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("booking.cancelled: user not found → silent no-op")
    void bookingCancelled_userNotFound_silentNoOp() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        BookingCancelledEvent event = new BookingCancelledEvent(202L, 999L, 6L, "test");
        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("booking.cancelled: duplicate event → idempotency key present, no second save")
    void bookingCancelled_duplicate_idempotentSkipsSave() throws Exception {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("totalBookings", 1L);
        prefs.put("processed:booking.cancelled:200", true);
        User user = userWithPrefs(2L, prefs);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        BookingCancelledEvent event = new BookingCancelledEvent(200L, 2L, 6L, "test");
        consumer.handleBookingEvent(toMessage(event), "booking.cancelled");

        verify(userRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Unknown routing key — acked and ignored
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Unknown routing key booking.placed → no DB write, no exception")
    void unknownRoutingKey_isAckedAndIgnored() throws Exception {
        BookingCompletedEvent event = new BookingCompletedEvent(300L, 1L, 5L, BigDecimal.TEN);

        consumer.handleBookingEvent(toMessage(event), "booking.placed");

        verifyNoInteractions(userRepository);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Malformed message body → RuntimeException (triggers DLQ in production)
    // ══════════════════════════════════════════════════════════════════════

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
