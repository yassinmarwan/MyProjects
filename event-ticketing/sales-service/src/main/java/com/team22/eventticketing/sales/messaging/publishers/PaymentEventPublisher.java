package com.team22.eventticketing.sales.messaging.publishers;

import com.team22.eventticketing.sales.config.PaymentEventConfig;
import com.team22.eventticketing.contracts.events.PaymentCompletedEvent;
import com.team22.eventticketing.contracts.events.PaymentFailedEvent;
import com.team22.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team22.eventticketing.contracts.events.PaymentRefundedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentInitiated(Long saleId, Long bookingId, double amount) {
        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EVENTS_EXCHANGE,
                "payment.initiated",
                new PaymentInitiatedEvent(saleId, bookingId, BigDecimal.valueOf(amount)));
    }

    public void publishPaymentCompleted(Long saleId, Long bookingId, double amount) {
        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EVENTS_EXCHANGE,
                "payment.completed",
                new PaymentCompletedEvent(saleId, bookingId, BigDecimal.valueOf(amount)));
    }

    public void publishPaymentFailed(Long saleId, Long bookingId, String reason) {
        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EVENTS_EXCHANGE,
                "payment.failed",
                new PaymentFailedEvent(saleId, bookingId, reason));
    }

    public void publishPaymentRefunded(Long saleId, Long bookingId, double refundAmount) {
        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EVENTS_EXCHANGE,
                "payment.refunded",
                new PaymentRefundedEvent(saleId, bookingId, BigDecimal.valueOf(refundAmount)));
    }
}
