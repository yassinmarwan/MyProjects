package com.team22.eventticketing.sales.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "auth_events")
public class AuthEvent implements MongoEvent {

    @Id
    private String id;
    private Instant timestamp;
    private String action;
    private Map<String, Object> details;
    private Long userId;
    private String email;

    public AuthEvent() {}

    public AuthEvent(String action, Map<String, Object> details, Long userId, String email) {
        this.timestamp = Instant.now();
        this.action = action;
        this.details = details;
        this.userId = userId;
        this.email = email;
    }

    @Override public String getId() { return id; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public String getAction() { return action; }
    @Override public Map<String, Object> getDetails() { return details; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }

    public void setId(String id) { this.id = id; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setAction(String action) { this.action = action; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setEmail(String email) { this.email = email; }
}
