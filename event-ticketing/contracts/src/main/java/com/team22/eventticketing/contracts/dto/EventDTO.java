package com.team22.eventticketing.contracts.dto;
import java.time.LocalDateTime;

public record EventDTO(Long id, String name, String status, LocalDateTime eventDate) {
    @Override
    public Long id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public LocalDateTime eventDate() {
        return eventDate;
    }
}