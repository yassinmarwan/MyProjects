// TicketStatusChangedEvent.java
package com.team22.eventticketing.contracts.events;
public record TicketStatusChangedEvent(Long ticketId, Long bookingId, String newStatus) {}