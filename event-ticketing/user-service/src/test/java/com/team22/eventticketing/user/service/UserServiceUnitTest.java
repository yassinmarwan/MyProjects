package com.team22.eventticketing.user.service;

import com.team22.eventticketing.user.dto.UserProfileDTO;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.messaging.UserEventPublisher;
import com.team22.eventticketing.user.repository.AuthEventRepository;
import com.team22.eventticketing.user.repository.UserRepository;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock UserRepository userRepository;
    @Mock AuthEventRepository authEventRepository;
    @Mock CacheEvictionService cacheEviction;
    @Mock BookingServiceClient bookingServiceClient;
    @Mock UserEventPublisher userEventPublisher;
    @Mock BookingServiceFallback fallback;
    @Mock CircuitBreakerFactory circuitBreakerFactory;
    @Mock CircuitBreaker circuitBreaker;

    UserService userService;

    @BeforeEach
    void setUp() {
        when(circuitBreakerFactory.create("bookingService")).thenReturn(circuitBreaker);
        // Make the circuit breaker execute the supplier directly (no actual circuit breaking in tests)
        lenient().when(circuitBreaker.run(any(Supplier.class), any(Function.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());

        userService = new UserService(
                userRepository, authEventRepository, cacheEviction,
                bookingServiceClient, userEventPublisher, fallback, circuitBreakerFactory);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User activeUser(Long id) {
        User u = new User("Alice", "alice@example.com", "hashed", "01012345678", Role.ATTENDEE);
        u.setId(id);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    // ══════════════════════════════════════════════════════════════════════
    // findById
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("findById: user exists → returns user")
    void findById_found_returnsUser() {
        User user = activeUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("findById: user not found → 404 NOT_FOUND")
    void findById_notFound_throws404() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    // ══════════════════════════════════════════════════════════════════════
    // update
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("update: applies patch fields, saves, evicts cache")
    void update_happyPath_savesAndEvictsCache() {
        User existing = activeUser(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User patch = new User(null, null, null, null, null);
        patch.setName("Bob");

        User result = userService.update(2L, patch);

        assertThat(result.getName()).isEqualTo("Bob");
        verify(userRepository).save(any(User.class));
        verify(cacheEviction).evictEntityAndFeatures(eq("user"), eq(2L), any(String[].class));
    }

    @Test
    @DisplayName("update: user not found → 404 NOT_FOUND; repository.save never called")
    void update_userNotFound_throws404() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // delete
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("delete: user found → deletes and evicts cache")
    void delete_found_deletesAndEvictsCache() {
        when(userRepository.existsById(3L)).thenReturn(true);

        userService.delete(3L);

        verify(userRepository).deleteById(3L);
        verify(cacheEviction).evictEntityAndFeatures(eq("user"), eq(3L), any(String[].class));
    }

    @Test
    @DisplayName("delete: user not found → 404 NOT_FOUND; deleteById never called")
    void delete_notFound_throws404() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).deleteById(any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // deactivateUser
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deactivateUser: no active bookings → status set to DEACTIVATED, event published")
    void deactivateUser_noActiveBookings_deactivatesAndPublishesEvent() {
        User user = activeUser(4L);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getActiveBookingCount(4L)).thenReturn(0);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.deactivateUser(4L);

        assertThat(result.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        verify(userEventPublisher).publishUserDeactivated(4L);
        verify(cacheEviction).evictEntityAndFeatures(eq("user"), eq(4L), any(String[].class));
    }

    @Test
    @DisplayName("deactivateUser: has active bookings → 400 BAD_REQUEST; no status change")
    void deactivateUser_hasActiveBookings_throws400() {
        User user = activeUser(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getActiveBookingCount(5L)).thenReturn(2);

        assertThatThrownBy(() -> userService.deactivateUser(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active bookings");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userEventPublisher);
    }

    // ══════════════════════════════════════════════════════════════════════
    // getProfile
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProfile: user found → returns UserProfileDTO with empty venues list")
    void getProfile_found_returnsProfile() {
        User user = activeUser(6L);
        when(userRepository.findByIdWithVenues(6L)).thenReturn(Optional.of(user));

        UserProfileDTO profile = userService.getProfile(6L);

        assertThat(profile.getUserId()).isEqualTo(6L);
        assertThat(profile.getEmail()).isEqualTo("alice@example.com");
        assertThat(profile.getFavoriteVenues()).isEmpty();
    }

    @Test
    @DisplayName("getProfile: user not found → 404 NOT_FOUND")
    void getProfile_notFound_throws404() {
        when(userRepository.findByIdWithVenues(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }
}
