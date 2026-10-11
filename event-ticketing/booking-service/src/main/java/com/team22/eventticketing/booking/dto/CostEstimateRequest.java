package com.team22.eventticketing.booking.dto;

public class CostEstimateRequest {
    private Long eventId;
    private Integer ticketCount;
    private String ticketTier;

    // TODO: Add standard Getters and Setters (or use Lombok @Data)


    public String getTicketTier() {
        return ticketTier;
    }

    public void setTicketTier(String ticketTier) {
        this.ticketTier = ticketTier;
    }

    public Integer getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(Integer ticketCount) {
        this.ticketCount = ticketCount;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }
}