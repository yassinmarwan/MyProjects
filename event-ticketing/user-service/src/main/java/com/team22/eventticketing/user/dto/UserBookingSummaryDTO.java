package com.team22.eventticketing.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserBookingSummaryDTO {

    private Long userId;
    private String name;
    private Long totalBookings;
    private Long completedBookings;
    private Long cancelledBookings;
    private Double totalSpent;
    private Double averageBookingAmount;

    public UserBookingSummaryDTO() {}

    public UserBookingSummaryDTO(Builder builder) {
        this.userId = builder.userId;
        this.name = builder.name;
        this.totalBookings = builder.totalBookings;
        this.completedBookings = builder.completedBookings;
        this.cancelledBookings = builder.cancelledBookings;
        this.totalSpent = builder.totalSpent;
        this.averageBookingAmount = builder.averageBookingAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private String name;
        private Long totalBookings;
        private Long completedBookings;
        private Long cancelledBookings;
        private Double totalSpent;
        private Double averageBookingAmount;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
        public Builder completedBookings(Long completedBookings) { this.completedBookings = completedBookings; return this; }
        public Builder cancelledBookings(Long cancelledBookings) { this.cancelledBookings = cancelledBookings; return this; }
        public Builder totalSpent(Double totalSpent) { this.totalSpent = totalSpent; return this; }
        public Builder averageBookingAmount(Double averageBookingAmount) { this.averageBookingAmount = averageBookingAmount; return this; }

        public UserBookingSummaryDTO build() {
            return new UserBookingSummaryDTO(this);
        }
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Long getTotalBookings() { return totalBookings; }
    public Long getCompletedBookings() { return completedBookings; }
    public Long getCancelledBookings() { return cancelledBookings; }
    public Double getTotalSpent() { return totalSpent; }
    public Double getAverageBookingAmount() { return averageBookingAmount; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setName(String name) { this.name = name; }
    public void setTotalBookings(Long totalBookings) { this.totalBookings = totalBookings; }
    public void setCompletedBookings(Long completedBookings) { this.completedBookings = completedBookings; }
    public void setCancelledBookings(Long cancelledBookings) { this.cancelledBookings = cancelledBookings; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public void setAverageBookingAmount(Double averageBookingAmount) { this.averageBookingAmount = averageBookingAmount; }
}
