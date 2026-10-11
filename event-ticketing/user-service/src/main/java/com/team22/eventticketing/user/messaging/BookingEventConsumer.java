package com.team22.eventticketing.user.messaging;

import tools.jackson.databind.ObjectMapper;
import com.team22.eventticketing.user.config.RabbitMQConfig;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.repository.UserRepository;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import jakarta.transaction.Transactional;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BookingEventConsumer {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public BookingEventConsumer(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.SAGA_QUEUE)
    @Transactional
    public void handleBookingEvent(Message message,
                                   @Header("amqp_receivedRoutingKey") String routingKey) {
        try {
            if ("booking.completed".equals(routingKey)) {
                BookingCompletedEvent event = objectMapper.readValue(message.getBody(), BookingCompletedEvent.class);
                handleCompleted(event);
            } else if ("booking.cancelled".equals(routingKey)) {
                BookingCancelledEvent event = objectMapper.readValue(message.getBody(), BookingCancelledEvent.class);
                handleCancelled(event);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process booking event: " + routingKey, e);
        }
    }

    private void handleCompleted(BookingCompletedEvent event) {
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) return;

        Map<String, Object> prefs = user.getPreferences();
        String key = "processed:booking.completed:" + event.bookingId();
        if (Boolean.TRUE.equals(prefs.get(key))) return;

        long total = toLong(prefs.getOrDefault("totalBookings", 0)) + 1;
        double spent = toDouble(prefs.getOrDefault("totalSpent", 0.0)) + event.totalAmount().doubleValue();
        prefs.put("totalBookings", total);
        prefs.put("totalSpent", spent);
        prefs.put(key, true);
        userRepository.save(user);
    }

    private void handleCancelled(BookingCancelledEvent event) {
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) return;

        Map<String, Object> prefs = user.getPreferences();
        String key = "processed:booking.cancelled:" + event.bookingId();
        if (Boolean.TRUE.equals(prefs.get(key))) return;

        // No totalSpent reversal — cancelled bookings were never COMPLETED so totalSpent was never incremented
        long total = Math.max(0, toLong(prefs.getOrDefault("totalBookings", 0)) - 1);
        prefs.put("totalBookings", total);
        prefs.put(key, true);
        userRepository.save(user);
    }

    private long toLong(Object v) { return Long.parseLong(v.toString()); }
    private double toDouble(Object v) { return Double.parseDouble(v.toString()); }
}
