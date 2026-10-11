package com.team22.eventticketing.contracts.dto;
import java.math.BigDecimal;

public record BookingSummaryDTO(long totalBookings, long completedBookings, long cancelledBookings, BigDecimal totalSpent, BigDecimal averageBookingAmount) {}