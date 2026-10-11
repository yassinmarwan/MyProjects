package com.team22.eventticketing.ticket.messaging;

import com.team22.eventticketing.ticket.config.RabbitConfig;
import com.team22.eventticketing.contracts.events.TicketCancelledEvent;
import com.team22.eventticketing.contracts.events.TicketIssuedEvent;
import com.team22.eventticketing.contracts.events.TicketStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TicketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTicketIssued(TicketIssuedEvent event) {
        publish(RabbitConfig.RK_TICKET_ISSUED, event);
    }

    public void publishTicketStatusChanged(TicketStatusChangedEvent event) {
        publish(RabbitConfig.RK_TICKET_STATUS_CHANGED, event);
    }

    public void publishTicketCancelled(TicketCancelledEvent event) {
        publish(RabbitConfig.RK_TICKET_CANCELLED, event);
    }

    private void publish(String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.TICKET_EVENTS_EXCHANGE, routingKey, payload);
            log.debug("Published {} -> {}", routingKey, payload);
        } catch (Exception e) {
            log.warn("Failed to publish {} ({}): {}", routingKey, payload, e.getMessage());
        }
    }
}
