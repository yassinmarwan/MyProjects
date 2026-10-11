package com.team22.eventticketing.booking.model;

public enum BookingStatus {
    PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED,
    COMPLETING,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    REFUNDED
}
