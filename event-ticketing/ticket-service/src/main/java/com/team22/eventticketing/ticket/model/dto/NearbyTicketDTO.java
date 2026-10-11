package com.team22.eventticketing.ticket.model.dto;

import java.util.List;
import java.util.Map;

public class NearbyTicketDTO {

    private Long ticketId;
    private String attendeeName;
    private Long bookingId;
    private String eventName;
    private Double eventLat;
    private Double eventLon;
    private Double distanceKm;

//    public NearbyTicketDTO(Long ticketId, String attendeeName, Long bookingId,
//                           String eventName, Double eventLat, Double eventLon,
//                           Double distanceKm) {
//        this.ticketId = ticketId;
//        this.attendeeName = attendeeName;
//        this.bookingId = bookingId;
//        this.eventName = eventName;
//        this.eventLat = eventLat;
//        this.eventLon = eventLon;
//        this.distanceKm = distanceKm;
//    }

    public NearbyTicketDTO(Builder builder){
        this.ticketId = builder.ticketId;
        this.attendeeName = builder.attendeeName;
        this.bookingId = builder.bookingId;
        this.eventName = builder.eventName;
        this.eventLat = builder.eventLat;
        this.eventLon = builder.eventLon;
        this.distanceKm = builder.distanceKm;
    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private Long ticketId;
        private String attendeeName;
        private Long bookingId;
        private String eventName;
        private Double eventLat;
        private Double eventLon;
        private Double distanceKm;

        public Builder ticketId(Long ticketId){this.ticketId=ticketId;return this;}
        public Builder attendeeName(String attendeeName){this.attendeeName=attendeeName;return this;}
        public Builder bookingId(Long bookingId){this.bookingId=bookingId;return this;}
        public Builder eventName(String eventName){this.eventName=eventName;return this;}
        public Builder eventLat(Double eventLat){this.eventLat=eventLat;return this;}
        public Builder eventLon(Double eventLon){this.eventLon=eventLon;return this;}
        public Builder distanceKm(Double distanceKm){this.distanceKm=distanceKm;return this;}

        public NearbyTicketDTO build() {
            return new NearbyTicketDTO(this);
        }

    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public String getEventName() {
        return eventName;
    }

    public Double getEventLat() {
        return eventLat;
    }

    public Double getEventLon() {
        return eventLon;
    }
}