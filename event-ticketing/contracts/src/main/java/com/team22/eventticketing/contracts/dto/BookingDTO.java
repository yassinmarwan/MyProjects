package com.team22.eventticketing.contracts.dto;
import java.math.BigDecimal;

public record BookingDTO(Long id, Long userId, Long eventId, String status, BigDecimal totalAmount) {
    @Override
    public Long id() {
        return id;
    }

    @Override
    public BigDecimal totalAmount() {
        return totalAmount;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public Long eventId() {
        return eventId;
    }

    @Override
    public Long userId() {
        return userId;
    }
}