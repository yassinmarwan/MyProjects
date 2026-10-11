// UserRegisteredEvent.java
package com.team22.eventticketing.contracts.events;
public record UserRegisteredEvent(Long userId, String email, String role) {}