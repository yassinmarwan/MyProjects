package com.team22.eventticketing.ticket.model.dto;

import java.util.List;

public class BatchTicketRequestDTO {

    private Long bookingId;
    private List<TicketItemDTO> tickets;

    public BatchTicketRequestDTO() {
    }

    public BatchTicketRequestDTO(Long bookingId, List<TicketItemDTO> tickets) {
        this.bookingId = bookingId;
        this.tickets = tickets;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public List<TicketItemDTO> getTickets() {
        return tickets;
    }

    public void setTickets(List<TicketItemDTO> tickets) {
        this.tickets = tickets;
    }
}