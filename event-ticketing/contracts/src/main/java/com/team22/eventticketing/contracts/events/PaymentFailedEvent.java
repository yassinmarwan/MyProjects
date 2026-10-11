// PaymentFailedEvent.java
package com.team22.eventticketing.contracts.events;
public record PaymentFailedEvent(Long saleId, Long bookingId, String reason) {}