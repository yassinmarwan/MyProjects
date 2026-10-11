package com.team22.eventticketing.sales.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "booking_events")
public class BookingEvent implements MongoEvent {

    @Id
    private String id;
    private Instant timestamp;
    private String action;
    private Map<String, Object> details;
    private Long bookingId;

    public BookingEvent() {}

    public BookingEvent(String action, Map<String, Object> details, Long bookingId) {
        this.timestamp = Instant.now();
        this.action = action;
        this.details = details;
        this.bookingId = bookingId;
    }

    @Override public String getId() { return id; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public String getAction() { return action; }
    @Override public Map<String, Object> getDetails() { return details; }
    public Long getBookingId() { return bookingId; }

    public void setId(String id) { this.id = id; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setAction(String action) { this.action = action; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
}
