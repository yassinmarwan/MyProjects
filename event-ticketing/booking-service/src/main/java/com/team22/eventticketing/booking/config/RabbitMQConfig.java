package com.team22.eventticketing.booking.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "booking.events";
    public static final String SAGA_FEEDBACK_QUEUE = "booking.saga-feedback";
    public static final String DLQ_NAME = "booking.saga-feedback.dlq";

    // 1. Tell RabbitMQ to use JSON for your DTOs/Records
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // 2. Create the Topic Exchange
    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // 3. Create the Queues (with DLQ routing)
    @Bean
    public Queue sagaFeedbackQueue() {
        return QueueBuilder.durable(SAGA_FEEDBACK_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    // 4. Bindings: Tell the feedback queue what to listen for
    @Bean
    public Binding bindPaymentInitiated(Queue sagaFeedbackQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(bookingExchange).with("payment.initiated");
    }

    @Bean
    public Binding bindPaymentCompleted(Queue sagaFeedbackQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(bookingExchange).with("payment.completed");
    }

    @Bean
    public Binding bindPaymentFailed(Queue sagaFeedbackQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(bookingExchange).with("payment.failed");
    }

    @Bean
    public Binding bindPaymentRefunded(Queue sagaFeedbackQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(bookingExchange).with("payment.refunded");
    }

    @Bean
    public Binding bindTicketIssued(Queue sagaFeedbackQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(sagaFeedbackQueue).to(bookingExchange).with("ticket.issued");
    }
}