package com.team22.eventticketing.user.dto;

import java.time.Instant;
import java.util.Map;

public class AuthEventDTO {

    private String id;
    private Instant timestamp;
    private String action;
    private Map<String, Object> details;
    private Long userId;
    private String email;

    public AuthEventDTO() {
    }

    public AuthEventDTO(String id, Instant timestamp, String action,
                        Map<String, Object> details, Long userId, String email) {
        this.id = id;
        this.timestamp = timestamp;
        this.action = action;
        this.details = details;
        this.userId = userId;
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}