package com.team22.eventticketing.ticket.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UnusedTicketDTO {
    private Long ticketId;
    private String attendeeName;
    private String ticketCode;
    private Long bookingId;
    private String eventName;
    private LocalDateTime eventDate;

    public UnusedTicketDTO(Long ticketId, String attendeeName, String ticketCode,
            Long bookingId, String eventName, LocalDateTime eventDate) {
        this.ticketId = ticketId;
        this.attendeeName = attendeeName;
        this.ticketCode = ticketCode;
        this.bookingId = bookingId;
        this.eventName = eventName;
        this.eventDate = eventDate;
    }

    public UnusedTicketDTO(Builder builder){
        this.ticketId = builder.ticketId;
        this.attendeeName = builder.attendeeName;
        this.ticketCode = builder.ticketCode;
        this.bookingId = builder.bookingId;
        this.eventName = builder.eventName;
        this.eventDate = builder.eventDate;
    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private Long ticketId;
        private String attendeeName;
        private String ticketCode;
        private Long bookingId;
        private String eventName;
        private LocalDateTime eventDate;

        public Builder ticketId(Long ticketId){this.ticketId=ticketId;return this;}
        public Builder attendeeName(String attendeeName){this.attendeeName=attendeeName;return this;}
        public Builder ticketCode(String ticketCode){this.ticketCode=ticketCode;return this;}
        public Builder bookingId(Long bookingId){this.bookingId=bookingId;return this;}
        public Builder eventName(String eventName){this.eventName=eventName;return this;}
        public Builder eventDate(LocalDateTime eventDate){this.eventDate=eventDate;return this;}

        public UnusedTicketDTO build() {
            return new UnusedTicketDTO(this);
        }

    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getEventName() {
        return eventName;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

}