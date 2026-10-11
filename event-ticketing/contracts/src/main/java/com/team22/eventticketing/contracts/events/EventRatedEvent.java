// EventRatedEvent.java
package com.team22.eventticketing.contracts.events;
public record EventRatedEvent(Long eventId, Long bookingId, Double rating, Long userId) {}