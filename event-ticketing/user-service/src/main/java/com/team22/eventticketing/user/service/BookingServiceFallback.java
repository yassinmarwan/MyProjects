package com.team22.eventticketing.user.service;

import com.team22.eventticketing.contracts.dto.BookingSummaryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BookingServiceFallback {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceFallback.class);

    public BookingSummaryDTO getUserBookingSummaryFallback(Long userId, Throwable t) {
        log.warn("booking-service circuit open — getUserBookingSummary for user {}: {}", userId, t.getMessage());
        return new BookingSummaryDTO(0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public BigDecimal getUserBookingTotalFallback(Long userId, Throwable t) {
        log.warn("booking-service circuit open — getUserBookingTotal for user {}: {}", userId, t.getMessage());
        return BigDecimal.ZERO;
    }

    public long getTotalBookingCountFallback(Long userId, Throwable t) {
        log.warn("booking-service circuit open — getTotalBookingCount for user {}: {}", userId, t.getMessage());
        return 0L;
    }

    public int getActiveBookingCountFallback(Long userId, Throwable t) {
        log.warn("booking-service circuit open — getActiveBookingCount for user {}: {}", userId, t.getMessage());
        return 0;
    }
}
