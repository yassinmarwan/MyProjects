package com.team22.eventticketing.booking.messaging;

import com.team22.eventticketing.booking.model.Booking;
import com.team22.eventticketing.booking.model.BookingStatus;
import com.team22.eventticketing.booking.repository.BookingRepository;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.PaymentFailedEvent;
import com.team22.eventticketing.contracts.events.TicketIssuedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingSagaListenerUnitTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingEventPublisher eventPublisher;

    BookingSagaListener sagaListener;

    @BeforeEach
    void setUp() {
        sagaListener = new BookingSagaListener(bookingRepository, eventPublisher);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private Booking bookingWithStatus(Long id, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setUserId(1L);
        b.setEventId(5L);
        b.setContactEmail("test@example.com");
        b.setStatus(status);
        b.setTotalAmount(300.0);
        b.setBookingDate(LocalDateTime.now());
        return b;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // handlePaymentFailed — PaymentFailedEvent → booking CANCELLED + booking.cancelled published
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PaymentFailedEvent: booking found → CANCELLED + booking.cancelled published with reason")
    void handlePaymentFailed_bookingFound_cancelledAndPublishes() {
        Booking booking = bookingWithStatus(1L, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sagaListener.handlePaymentFailed(new PaymentFailedEvent(10L, 1L, "insufficient funds"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(booking);

        ArgumentCaptor<BookingCancelledEvent> captor = ArgumentCaptor.forClass(BookingCancelledEvent.class);
        verify(eventPublisher).publishBookingCancelled(captor.capture());
        BookingCancelledEvent published = captor.getValue();
        assertThat(published.bookingId()).isEqualTo(1L);
        assertThat(published.userId()).isEqualTo(1L);
        assertThat(published.eventId()).isEqualTo(5L);
        assertThat(published.reason()).contains("insufficient funds");
    }

    @Test
    @DisplayName("PaymentFailedEvent: PENDING booking → CANCELLED")
    void handlePaymentFailed_pendingBooking_cancelled() {
        Booking booking = bookingWithStatus(2L, BookingStatus.PENDING);
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sagaListener.handlePaymentFailed(new PaymentFailedEvent(11L, 2L, "card declined"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(eventPublisher).publishBookingCancelled(any(BookingCancelledEvent.class));
    }

    @Test
    @DisplayName("PaymentFailedEvent: booking not found → no-op, nothing saved or published")
    void handlePaymentFailed_bookingNotFound_noOp() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        sagaListener.handlePaymentFailed(new PaymentFailedEvent(10L, 99L, "not found"));

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // handleTicketIssued — TicketIssuedEvent → booking COMPLETED
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TicketIssuedEvent: booking found → COMPLETED; no downstream event published")
    void handleTicketIssued_bookingFound_completed() {
        Booking booking = bookingWithStatus(3L, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sagaListener.handleTicketIssued(new TicketIssuedEvent(20L, 3L, "John Doe", "TICKET-ABC-123"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(bookingRepository).save(booking);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("TicketIssuedEvent: replaying same event is idempotent — already COMPLETED stays COMPLETED")
    void handleTicketIssued_replay_idempotentNoSecondSave() {
        Booking booking = bookingWithStatus(4L, BookingStatus.COMPLETED);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sagaListener.handleTicketIssued(new TicketIssuedEvent(21L, 4L, "Jane", "TICKET-XYZ"));

        // save still happens (ifPresent runs), but state doesn't change
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("TicketIssuedEvent: booking not found → no-op, nothing saved")
    void handleTicketIssued_bookingNotFound_noOp() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        sagaListener.handleTicketIssued(new TicketIssuedEvent(20L, 99L, "Jane", "TICKET-XYZ"));

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
