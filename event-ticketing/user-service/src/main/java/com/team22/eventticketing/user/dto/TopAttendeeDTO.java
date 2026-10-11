package com.team22.eventticketing.user.dto;

public class TopAttendeeDTO {

    private Long userId;
    private String name;
    private Double totalSpent;
    private Long bookingCount;

    public TopAttendeeDTO(Builder builder) {
        this.userId = builder.userId;
        this.name = builder.name;
        this.totalSpent = builder.totalSpent;
        this.bookingCount = builder.bookingCount;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private String name;
        private Double totalSpent;
        private Long bookingCount;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalSpent(Double totalSpent) { this.totalSpent = totalSpent; return this; }
        public Builder bookingCount(Long bookingCount) { this.bookingCount = bookingCount; return this; }

        public TopAttendeeDTO build() {
            return new TopAttendeeDTO(this);
        }
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Double getTotalSpent() { return totalSpent; }
    public Long getBookingCount() { return bookingCount; }
}
