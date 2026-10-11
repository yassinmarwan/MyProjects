package com.team22.eventticketing.sales.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "ticket_events")
public class TicketEvent implements MongoEvent {

    @Id
    private String id;
    private Instant timestamp;
    private String action;
    private Map<String, Object> details;
    private Long ticketId;

    public TicketEvent() {}

    public TicketEvent(String action, Map<String, Object> details, Long ticketId) {
        this.timestamp = Instant.now();
        this.action = action;
        this.details = details;
        this.ticketId = ticketId;
    }

    @Override public String getId() { return id; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public String getAction() { return action; }
    @Override public Map<String, Object> getDetails() { return details; }
    public Long getTicketId() { return ticketId; }

    public void setId(String id) { this.id = id; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setAction(String action) { this.action = action; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
}
