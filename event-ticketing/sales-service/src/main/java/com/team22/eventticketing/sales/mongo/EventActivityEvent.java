package com.team22.eventticketing.sales.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "event_events")
public class EventActivityEvent implements MongoEvent {

    @Id
    private String id;
    private Instant timestamp;
    private String action;
    private Map<String, Object> details;
    private Long eventId;

    public EventActivityEvent() {}

    public EventActivityEvent(String action, Map<String, Object> details, Long eventId) {
        this.timestamp = Instant.now();
        this.action = action;
        this.details = details;
        this.eventId = eventId;
    }

    @Override public String getId() { return id; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public String getAction() { return action; }
    @Override public Map<String, Object> getDetails() { return details; }
    public Long getEventId() { return eventId; }

    public void setId(String id) { this.id = id; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setAction(String action) { this.action = action; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
}
