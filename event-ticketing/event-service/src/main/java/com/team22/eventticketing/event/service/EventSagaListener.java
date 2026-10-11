package com.team22.eventticketing.event.service;

import com.team22.eventticketing.contracts.events.BookingPlacedEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.event.model.ProcessedEvent;
import com.team22.eventticketing.event.repository.EventRepository;
import com.team22.eventticketing.event.repository.ProcessedEventRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RabbitListener(queues = "event.booking.saga-listener") // FIXED: Moved to class level
public class EventSagaListener {

    private final EventRepository eventRepository;
    private final ProcessedEventRepository processedEventRepository;

    public EventSagaListener(EventRepository eventRepository, ProcessedEventRepository processedEventRepository) {
        this.eventRepository = eventRepository;
        this.processedEventRepository = processedEventRepository;
    }

    private void updateEventStat(Long eventId, String statKey, Number incrementAmount) {
        eventRepository.findById(eventId).ifPresent(event -> {
            Map<String, Object> details = event.getDetails();
            if (details == null) {
                details = new HashMap<>();
            }

            // Use Double for revenue/money to avoid BigDecimal casting crashes
            if (incrementAmount instanceof BigDecimal || incrementAmount instanceof Double) {
                double current = Double.parseDouble(details.getOrDefault(statKey, 0.0).toString());
                details.put(statKey, current + incrementAmount.doubleValue());
            } else {
                long current = Long.parseLong(details.getOrDefault(statKey, 0).toString());
                details.put(statKey, current + incrementAmount.longValue());
            }

            event.setDetails(details);
            eventRepository.save(event);
        });
    }

    @RabbitHandler // FIXED: Changed from RabbitListener
    @Transactional
    public void handleBookingPlaced(BookingPlacedEvent eventMessage) {
        String idempotencyKey = eventMessage.bookingId() + "-PLACED";

        if (processedEventRepository.existsById(idempotencyKey)) {
            System.out.println("Duplicate Placed event ignored: " + idempotencyKey);
            return;
        }

        updateEventStat(eventMessage.eventId(), "pendingBookings", 1L);
        processedEventRepository.save(new ProcessedEvent(idempotencyKey));
    }

    @RabbitHandler // FIXED: Changed from RabbitListener
    @Transactional
    public void handleBookingCompleted(BookingCompletedEvent eventMessage) {
        String idempotencyKey = eventMessage.bookingId() + "-COMPLETED";

        if (processedEventRepository.existsById(idempotencyKey)) {
            System.out.println("Duplicate Completed event ignored: " + idempotencyKey);
            return;
        }

        updateEventStat(eventMessage.eventId(), "totalCompletedBookings", 1L);
        if (eventMessage.totalAmount() != null) {
            updateEventStat(eventMessage.eventId(), "totalRevenue", eventMessage.totalAmount());
        }
        updateEventStat(eventMessage.eventId(), "pendingBookings", -1L);

        processedEventRepository.save(new ProcessedEvent(idempotencyKey));
    }

    @RabbitHandler // FIXED: Changed from RabbitListener
    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent eventMessage) {
        String idempotencyKey = eventMessage.bookingId() + "-CANCELLED";

        if (processedEventRepository.existsById(idempotencyKey)) {
            System.out.println("Duplicate Cancelled event ignored: " + idempotencyKey);
            return;
        }

        updateEventStat(eventMessage.eventId(), "pendingBookings", -1L);
        processedEventRepository.save(new ProcessedEvent(idempotencyKey));
    }
}