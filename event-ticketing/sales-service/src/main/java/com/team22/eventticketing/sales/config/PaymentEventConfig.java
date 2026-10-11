package com.team22.eventticketing.sales.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {

    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";
    public static final String SAGA_QUEUE = "payment.saga-listener";
    public static final String SAGA_DLQ   = "payment.saga-listener.dlq";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE);
    }

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue sagaQueue() {
        return QueueBuilder.durable(SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", SAGA_DLQ)
                .withArgument("x-dead-letter-routing-key", SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue sagaDLQ() {
        return QueueBuilder.durable(SAGA_DLQ).build();
    }

    @Bean
    public Binding sagaBinding(Queue sagaQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(sagaQueue).to(bookingEventsExchange).with("booking.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate t = new RabbitTemplate(factory);
        t.setMessageConverter(jsonMessageConverter);
        return t;
    }
}
