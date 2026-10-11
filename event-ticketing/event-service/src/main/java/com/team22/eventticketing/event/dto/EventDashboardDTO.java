package com.team22.eventticketing.event.dto;
//
//public class EventDashboardDTO {
//
//    private Long eventId;
//    private String name;
//    private Long totalBookings;
//    private Long totalTicketsSold;
//    private Double totalRevenue;
//    private Double averageAttendanceRate;
//    private Double averageRating;
//
//    public EventDashboardDTO(Long eventId, String name, Long totalBookings,
//                             Long totalTicketsSold, Double totalRevenue,
//                             Double averageAttendanceRate, Double averageRating) {
//        this.eventId = eventId;
//        this.name = name;
//        this.totalBookings = totalBookings;
//        this.totalTicketsSold = totalTicketsSold;
//        this.totalRevenue = totalRevenue;
//        this.averageAttendanceRate = averageAttendanceRate;
//        this.averageRating = averageRating;
//    }
//
//    public Long getEventId() { return eventId; }
//    public String getName() { return name; }
//    public Long getTotalBookings() { return totalBookings; }
//    public Long getTotalTicketsSold() { return totalTicketsSold; }
//    public Double getTotalRevenue() { return totalRevenue; }
//    public Double getAverageAttendanceRate() { return averageAttendanceRate; }
//    public Double getAverageRating() { return averageRating; }
//}


public class EventDashboardDTO {

    private Long eventId;
    private String name;
    private Long totalBookings;
    private Long totalTicketsSold;
    private Double totalRevenue;
    private Double averageAttendanceRate;
    private Double averageRating;

    public EventDashboardDTO(Builder builder) {
        this.eventId = builder.eventId;
        this.name = builder.name;
        this.totalBookings = builder.totalBookings;
        this.totalTicketsSold = builder.totalTicketsSold;
        this.totalRevenue = builder.totalRevenue;
        this.averageAttendanceRate = builder.averageAttendanceRate;
        this.averageRating = builder.averageRating;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long eventId;
        private String name;
        private Long totalBookings;
        private Long totalTicketsSold;
        private Double totalRevenue;
        private Double averageAttendanceRate;
        private Double averageRating;

        public Builder eventId(Long eventId) { this.eventId = eventId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
        public Builder totalTicketsSold(Long totalTicketsSold) { this.totalTicketsSold = totalTicketsSold; return this; }
        public Builder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder averageAttendanceRate(Double averageAttendanceRate) { this.averageAttendanceRate = averageAttendanceRate; return this; }
        public Builder averageRating(Double averageRating) { this.averageRating = averageRating; return this; }

        public EventDashboardDTO build() {
            return new EventDashboardDTO(this);
        }
    }

    // Getters
    public Long getEventId() { return eventId; }
    public String getName() { return name; }
    public Long getTotalBookings() { return totalBookings; }
    public Long getTotalTicketsSold() { return totalTicketsSold; }
    public Double getTotalRevenue() { return totalRevenue; }
    public Double getAverageAttendanceRate() { return averageAttendanceRate; }
    public Double getAverageRating() { return averageRating; }
}