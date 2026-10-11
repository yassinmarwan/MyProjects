package com.team22.eventticketing.ticket.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking_event_mapping")
public class BookingEventMapping {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    public BookingEventMapping() {
    }

    public BookingEventMapping(Long bookingId, Long eventId, LocalDateTime receivedAt) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.receivedAt = receivedAt;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
