// PaymentCompletedEvent.java
package com.team22.eventticketing.contracts.events;
import java.math.BigDecimal;
public record PaymentCompletedEvent(Long saleId, Long bookingId, BigDecimal amount) {}