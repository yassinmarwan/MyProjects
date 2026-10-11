package com.team22.eventticketing.event.dto;


import java.util.List;
import java.util.Map;

public class TopEventDTO {

    private Long eventId;
    private String name;
    private Double rating;
    private Long totalBookings;

//    public TopEventDTO() {
//    }
//
//    public TopEventDTO(Long eventId, String name, Double rating, Long totalBookings) {
//        this.eventId = eventId;
//        this.name = name;
//        this.rating = rating;
//        this.totalBookings = totalBookings;
//    }

    public TopEventDTO(Builder builder){
        this.eventId = builder.eventId;
        this.name = builder.name;
        this.rating = builder.rating;
        this.totalBookings = builder.totalBookings;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long eventId;
        private String name;
        private Double rating;
        private Long totalBookings;

        public Builder eventId(Long eventId) { this.eventId = eventId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder rating(Double rating) { this.rating = rating; return this; }
        public Builder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }

        public TopEventDTO build() {
            return new TopEventDTO(this);
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Long getTotalBookings() {
        return totalBookings;
    }

    public void setTotalBookings(Long totalBookings) {
        this.totalBookings = totalBookings;
    }
}