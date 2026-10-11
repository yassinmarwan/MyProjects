// PaymentInitiatedEvent.java
package com.team22.eventticketing.contracts.events;
import java.math.BigDecimal;
public record PaymentInitiatedEvent(Long saleId, Long bookingId, BigDecimal amount) {}