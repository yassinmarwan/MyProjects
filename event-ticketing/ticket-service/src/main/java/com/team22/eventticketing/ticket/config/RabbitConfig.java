package com.team22.eventticketing.ticket.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String TICKET_EVENTS_EXCHANGE = "ticket.events";

    public static final String RK_TICKET_ISSUED = "ticket.issued";
    public static final String RK_TICKET_STATUS_CHANGED = "ticket.status-changed";
    public static final String RK_TICKET_CANCELLED = "ticket.cancelled";

    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";

    public static final String RK_BOOKING_PLACED = "booking.placed";
    public static final String RK_BOOKING_COMPLETED = "booking.completed";
    public static final String RK_BOOKING_CANCELLED = "booking.cancelled";

    public static final String SAGA_QUEUE = "ticket.booking.saga-listener";
    public static final String SAGA_DLX = "ticket.booking.saga-listener.dlx";
    public static final String SAGA_DLQ = "ticket.booking.saga-listener.dlq";

    @Bean
    public TopicExchange ticketEventsExchange() {
        return new TopicExchange(TICKET_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange sagaDeadLetterExchange() {
        return new TopicExchange(SAGA_DLX, true, false);
    }

    @Bean
    public Queue sagaQueue() {
        return QueueBuilder.durable(SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", SAGA_DLX)
                .withArgument("x-dead-letter-routing-key", "dead")
                .build();
    }

    @Bean
    public Queue sagaDeadLetterQueue() {
        return QueueBuilder.durable(SAGA_DLQ).build();
    }

    @Bean
    public Binding sagaBindingPlaced(Queue sagaQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(sagaQueue).to(bookingEventsExchange).with(RK_BOOKING_PLACED);
    }

    @Bean
    public Binding sagaBindingCompleted(Queue sagaQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(sagaQueue).to(bookingEventsExchange).with(RK_BOOKING_COMPLETED);
    }

    @Bean
    public Binding sagaBindingCancelled(Queue sagaQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(sagaQueue).to(bookingEventsExchange).with(RK_BOOKING_CANCELLED);
    }

    @Bean
    public Binding sagaDeadLetterBinding(Queue sagaDeadLetterQueue, TopicExchange sagaDeadLetterExchange) {
        return BindingBuilder.bind(sagaDeadLetterQueue).to(sagaDeadLetterExchange).with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
