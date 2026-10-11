// PaymentRefundedEvent.java
package com.team22.eventticketing.contracts.events;
import java.math.BigDecimal;
public record PaymentRefundedEvent(Long saleId, Long bookingId, BigDecimal refundAmount) {}