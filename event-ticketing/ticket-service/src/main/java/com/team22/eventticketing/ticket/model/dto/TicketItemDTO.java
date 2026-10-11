package com.team22.eventticketing.ticket.model.dto;

import java.util.Map;

public class TicketItemDTO {

    private String attendeeName;
    private String ticketCode;
    private Map<String, Object> metadata;

    public TicketItemDTO() {
    }

    public TicketItemDTO(String attendeeName, String ticketCode, Map<String, Object> metadata) {
        this.attendeeName = attendeeName;
        this.ticketCode = ticketCode;
        this.metadata = metadata;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public void setAttendeeName(String attendeeName) {
        this.attendeeName = attendeeName;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}