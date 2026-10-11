package com.team22.eventticketing.event.dto;

import com.team22.eventticketing.event.model.EventStatus;

public class UpdateEventStatusRequest {

    private EventStatus status;

    public UpdateEventStatusRequest() {
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }
}