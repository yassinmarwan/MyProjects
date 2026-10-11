// EventStatusChangedEvent.java
package com.team22.eventticketing.contracts.events;
public record EventStatusChangedEvent(Long eventId, String oldStatus, String newStatus) {}