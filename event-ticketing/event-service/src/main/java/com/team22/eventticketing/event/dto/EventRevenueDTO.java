package com.team22.eventticketing.event.dto;

public class EventRevenueDTO {

    private Long eventId;
    private String name;
    private long totalBookings;
    private Double totalRevenue;
    private Double averageBookingAmount;

//    public EventRevenueDTO(Long eventId, String name,
//                           long totalBookings,
//                           Double totalRevenue,
//                           Double averageBookingAmount) {
//        this.eventId = eventId;
//        this.name = name;
//        this.totalBookings = totalBookings;
//        this.totalRevenue = totalRevenue;
//        this.averageBookingAmount = averageBookingAmount;
//    }

    public EventRevenueDTO(Builder builder){
        this.eventId = builder.eventId;
        this.name = builder.name;
        this.totalBookings = builder.totalBookings;
        this.totalRevenue = builder.totalRevenue;
        this.averageBookingAmount = builder.averageBookingAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long eventId;
        private String name;
        private Long totalBookings;
        private Double totalRevenue;
        private Double averageBookingAmount;

        public Builder eventId(Long eventId) { this.eventId = eventId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
        public Builder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder averageBookingAmount(Double averageBookingAmount) { this.averageBookingAmount = averageBookingAmount; return this; }

        public EventRevenueDTO build() {
            return new EventRevenueDTO(this);
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public long getTotalBookings() {
        return totalBookings;
    }

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public Double getAverageBookingAmount() {
        return averageBookingAmount;
    }
// getters
}