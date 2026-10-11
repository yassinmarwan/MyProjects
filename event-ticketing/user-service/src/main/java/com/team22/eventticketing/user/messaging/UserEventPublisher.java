package com.team22.eventticketing.user.messaging;

import com.team22.eventticketing.user.config.RabbitMQConfig;
import com.team22.eventticketing.contracts.events.UserDeactivatedEvent;
import com.team22.eventticketing.contracts.events.UserRegisteredEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(Long userId, String email, String role) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.USER_EVENTS_EXCHANGE,
                "user.registered",
                new UserRegisteredEvent(userId, email, role)
        );
    }

    public void publishUserDeactivated(Long userId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.USER_EVENTS_EXCHANGE,
                "user.deactivated",
                new UserDeactivatedEvent(userId)
        );
    }
}
