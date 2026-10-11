package com.team22.eventticketing.event.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter; // Using your preferred import
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter(); // Keeping your preferred converter
    }

    // --- EXCHANGES ---
    @Bean
    public TopicExchange eventExchange() {
        // Slice 5: Used for publishing event.status-changed
        return new TopicExchange("event.events");
    }

    @Bean
    public TopicExchange bookingExchange() {
        // Slice 5: Used for listening to booking.placed, completed, etc.
        return new TopicExchange("booking.events");
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange("event.dlx");
    }

    // --- QUEUES ---
    @Bean
    public Queue sagaListenerQueue() {
        return QueueBuilder.durable("event.booking.saga-listener")
                .withArgument("x-dead-letter-exchange", "event.dlx")
                .withArgument("x-dead-letter-routing-key", "event.booking.saga-listener.dlq")
                .build();
    }

    @Bean
    public Queue sagaListenerDlq() {
        return QueueBuilder.durable("event.booking.saga-listener.dlq").build();
    }

    // --- BINDINGS (For your SagaListener to work) ---
    @Bean
    public Binding bindDlq() {
        return BindingBuilder.bind(sagaListenerDlq())
                .to(deadLetterExchange())
                .with("event.booking.saga-listener.dlq");
    }

    @Bean
    public Binding bindPlaced() {
        return BindingBuilder.bind(sagaListenerQueue())
                .to(bookingExchange())
                .with("booking.placed");
    }

    @Bean
    public Binding bindCompleted() {
        return BindingBuilder.bind(sagaListenerQueue())
                .to(bookingExchange())
                .with("booking.completed");
    }

    @Bean
    public Binding bindCancelled() {
        return BindingBuilder.bind(sagaListenerQueue())
                .to(bookingExchange())
                .with("booking.cancelled");
    }
}