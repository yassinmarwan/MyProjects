package com.team22.eventticketing.booking.messaging;

import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.events.BookingPlacedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE = "booking.events";

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // Call this from your Service class when a booking is created (S3-F4)
    public void publishBookingPlaced(BookingPlacedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "booking.placed", event);
    }

    // Call this when the whole saga finishes successfully
    public void publishBookingCompleted(BookingCompletedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "booking.completed", event);
    }

    // Call this if the user cancels (S3-F7) or if the saga fails
    public void publishBookingCancelled(BookingCancelledEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "booking.cancelled", event);
    }
}