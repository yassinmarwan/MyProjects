// BookingPlacedEvent.java
package com.team22.eventticketing.contracts.events;
public record BookingPlacedEvent(Long bookingId, Long userId, Long eventId) {}