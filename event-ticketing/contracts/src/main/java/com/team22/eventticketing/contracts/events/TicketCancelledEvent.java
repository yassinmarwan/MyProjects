// TicketCancelledEvent.java
package com.team22.eventticketing.contracts.events;
public record TicketCancelledEvent(Long ticketId, Long bookingId) {}