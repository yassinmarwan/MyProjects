package com.team22.eventticketing.booking.dto;

import com.team22.eventticketing.booking.model.BookingStatus;

import java.io.Serializable;
import java.util.Map;

public class BookingAnalyticsDashboardDTO implements Serializable {
    // 1. ADD THIS: It acts as a version control ID for binary serialization
    private static final long serialVersionUID = 1L;

    Long totalBookings;
    Double totalRevenue;
    Double averageBookingValue;
    Double conversionRate;
    Map<BookingStatus, Long> bookingsByStatus;

    public BookingAnalyticsDashboardDTO(Builder builder){
        this.totalBookings=builder.totalBookings;
        this.totalRevenue=builder.totalRevenue;
        this.averageBookingValue=builder.averageBookingValue;
        this.conversionRate=builder.conversionRate;
        this.bookingsByStatus=builder.bookingsByStatus;
    }

    public static class Builder {
        private Long totalBookings;
        private Double totalRevenue;
        private Double averageBookingValue;
        private Double conversionRate;
        private Map<BookingStatus, Long> bookingsByStatus;

        public Builder totalBookings(Long totalBookings) {
            this.totalBookings = totalBookings;
            return this;
        }

        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageBookingValue(Double averageBookingValue) {
            this.averageBookingValue = averageBookingValue;
            return this;
        }

        public Builder conversionRate(Double conversionRate) {
            this.conversionRate = conversionRate;
            return this;
        }

        public Builder bookingsByStatus(Map<BookingStatus, Long> bookingsByStatus) {
            this.bookingsByStatus = bookingsByStatus;
            return this;
        }

        public BookingAnalyticsDashboardDTO build() {
            return new BookingAnalyticsDashboardDTO(this);
        }
    }

    // Static method to initialize the builder
    public static Builder builder() {
        return new Builder();
    }

    public Long getTotalBookings() {
        return totalBookings;
    }

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public Double getAverageBookingValue() {
        return averageBookingValue;
    }

    public Double getConversionRate() {
        return conversionRate;
    }

    public Map<BookingStatus, Long> getBookingsByStatus() {
        return bookingsByStatus;
    }
}
