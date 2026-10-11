package com.team22.eventticketing.booking.service;

import com.team22.eventticketing.booking.adapter.AnalyticsDashboardTarget;
import com.team22.eventticketing.booking.messaging.BookingEventPublisher;
import com.team22.eventticketing.booking.model.Booking;
import com.team22.eventticketing.booking.model.BookingStatus;
import com.team22.eventticketing.booking.repository.BookingItemRepository;
import com.team22.eventticketing.booking.repository.BookingRepository;
import com.team22.eventticketing.booking.repository.EventNodeRepository;
import com.team22.eventticketing.booking.repository.UserNodeCustomRepository;
import com.team22.eventticketing.booking.repository.UserNodeRecommendationRepository;
import com.team22.eventticketing.booking.repository.UserNodeRepository;
import com.team22.eventticketing.contracts.dto.EventDTO;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.feign.TicketServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceUnitTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingItemRepository bookingItemRepository;
    @Mock CacheEvictionService cacheEviction;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock AnalyticsDashboardTarget dashboardAdapter;
    @Mock UserNodeRepository userNodeRepository;
    @Mock EventNodeRepository eventNodeRepository;
    @Mock UserNodeCustomRepository userNodeCustomRepository;
    @Mock UserNodeRecommendationRepository userNodeRecommendationRepository;
    @Mock EventServiceClient eventServiceClient;
    @Mock UserServiceClient userServiceClient;
    @Mock TicketServiceClient ticketServiceClient;
    @Mock BookingEventPublisher eventPublisher;

    BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                dashboardAdapter,
                bookingItemRepository,
                cacheEviction,
                redisTemplate,
                eventNodeRepository,
                userNodeRepository,
                List.of(),
                userNodeCustomRepository,
                userNodeRecommendationRepository,
                eventServiceClient,
                userServiceClient,
                ticketServiceClient,
                eventPublisher
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private Booking bookingWithStatus(Long id, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setUserId(1L);
        b.setEventId(5L);
        b.setContactEmail("test@example.com");
        b.setStatus(status);
        b.setTotalAmount(500.0);
        b.setBookingDate(LocalDateTime.now());
        b.setBookingItems(List.of());
        return b;
    }

    private EventDTO eventWithStatus(Long id, String status) {
        return new EventDTO(id, "Test Event", status, LocalDateTime.now().plusDays(10));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // [S3-F4] completeBooking — CHECKED_IN → COMPLETED
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Happy path: CHECKED_IN booking → COMPLETED + booking.completed published with correct fields")
    void completeBooking_checkedIn_completesAndPublishes() {
        Booking booking = bookingWithStatus(1L, BookingStatus.CHECKED_IN);
        when(bookingRepository.findByIdWithItems(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.completeBooking(1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(bookingRepository).insertTicketSale(eq(1L), eq(1L), anyDouble());

        ArgumentCaptor<BookingCompletedEvent> captor = ArgumentCaptor.forClass(BookingCompletedEvent.class);
        verify(eventPublisher).publishBookingCompleted(captor.capture());
        BookingCompletedEvent published = captor.getValue();
        assertThat(published.bookingId()).isEqualTo(1L);
        assertThat(published.userId()).isEqualTo(1L);
        assertThat(published.eventId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Booking not found → 404 NOT_FOUND; publisher never called")
    void completeBooking_notFound_throws404() {
        when(bookingRepository.findByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.completeBooking(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Non-CHECKED_IN booking (PENDING) → 400 BAD_REQUEST; publisher never called")
    void completeBooking_wrongStatus_throws400() {
        Booking booking = bookingWithStatus(2L, BookingStatus.PENDING);
        when(bookingRepository.findByIdWithItems(2L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.completeBooking(2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CHECKED_IN");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("CONFIRMED booking → 400 BAD_REQUEST; only CHECKED_IN allowed")
    void completeBooking_confirmedStatus_throws400() {
        Booking booking = bookingWithStatus(3L, BookingStatus.CONFIRMED);
        when(bookingRepository.findByIdWithItems(3L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.completeBooking(3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CHECKED_IN");

        verifyNoInteractions(eventPublisher);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // [S3-F2] cancelBooking — PENDING/CONFIRMED → CANCELLED
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PENDING booking → CANCELLED + booking.cancelled published + tickets cancelled")
    void cancelBooking_pending_cancelsAndPublishes() {
        Booking booking = bookingWithStatus(10L, BookingStatus.PENDING);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(10L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).cancelValidTicketsForBooking(10L);

        ArgumentCaptor<BookingCancelledEvent> captor = ArgumentCaptor.forClass(BookingCancelledEvent.class);
        verify(eventPublisher).publishBookingCancelled(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(10L);
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().eventId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("CONFIRMED booking → CANCELLED + booking.cancelled published")
    void cancelBooking_confirmed_cancelsAndPublishes() {
        Booking booking = bookingWithStatus(11L, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(11L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(eventPublisher).publishBookingCancelled(any(BookingCancelledEvent.class));
    }

    @Test
    @DisplayName("COMPLETED booking → 400 BAD_REQUEST; publisher never called")
    void cancelBooking_completedStatus_throws400() {
        Booking booking = bookingWithStatus(12L, BookingStatus.COMPLETED);
        when(bookingRepository.findById(12L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(12L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PENDING or CONFIRMED");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Booking not found → 404 NOT_FOUND; publisher never called")
    void cancelBooking_notFound_throws404() {
        when(bookingRepository.findById(98L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(98L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");

        verifyNoInteractions(eventPublisher);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // [S3-F7] confirmBooking — PENDING + UPCOMING event → CONFIRMED
    // NOTE: code calls Feign BEFORE checking booking status
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Happy path: PENDING booking + UPCOMING event → CONFIRMED with confirmedAt set")
    void confirmBooking_pendingAndUpcoming_confirms() {
        Booking booking = bookingWithStatus(20L, BookingStatus.PENDING);
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenReturn(eventWithStatus(5L, "UPCOMING"));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.confirmBooking(20L, 5L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getEventId()).isEqualTo(5L);
        assertThat(result.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("Booking not found → 404 NOT_FOUND; Feign never called")
    void confirmBooking_notFound_throws404() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(99L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");

        verifyNoInteractions(eventServiceClient);
    }

    @Test
    @DisplayName("Non-PENDING booking (CONFIRMED) → 400 BAD_REQUEST")
    void confirmBooking_wrongStatus_throws400() {
        Booking booking = bookingWithStatus(21L, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(21L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenReturn(eventWithStatus(5L, "UPCOMING"));

        assertThatThrownBy(() -> bookingService.confirmBooking(21L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not PENDING");
    }

    @Test
    @DisplayName("Event not UPCOMING → 400 BAD_REQUEST")
    void confirmBooking_eventNotUpcoming_throws400() {
        Booking booking = bookingWithStatus(22L, BookingStatus.PENDING);
        when(bookingRepository.findById(22L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenReturn(eventWithStatus(5L, "CANCELLED"));

        assertThatThrownBy(() -> bookingService.confirmBooking(22L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not UPCOMING");
    }

    @Test
    @DisplayName("Feign 404 for event → 404 ResponseStatusException with 'Event not found'")
    void confirmBooking_eventFeignNotFound_throws404() {
        Booking booking = bookingWithStatus(23L, BookingStatus.PENDING);
        when(bookingRepository.findById(23L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenThrow(mock(feign.FeignException.NotFound.class));

        assertThatThrownBy(() -> bookingService.confirmBooking(23L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    @DisplayName("Feign generic error → 503 SERVICE_UNAVAILABLE")
    void confirmBooking_eventFeignUnavailable_throws503() {
        Booking booking = bookingWithStatus(24L, BookingStatus.PENDING);
        when(bookingRepository.findById(24L)).thenReturn(Optional.of(booking));
        feign.FeignException feignEx = mock(feign.FeignException.class);
        when(eventServiceClient.getEvent(5L)).thenThrow(feignEx);

        assertThatThrownBy(() -> bookingService.confirmBooking(24L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unavailable");
    }
}
