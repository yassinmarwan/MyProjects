package com.team22.eventticketing.contracts.dto;
import java.math.BigDecimal;

public record BookingItemDTO(String ticketTier, Integer quantity, BigDecimal unitPrice) {}