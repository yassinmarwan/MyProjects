package com.team22.eventticketing.contracts.dto;
import java.math.BigDecimal;

public record EventBookingRevenueDTO(long totalBookings, BigDecimal totalRevenue, BigDecimal averageBookingAmount) {}