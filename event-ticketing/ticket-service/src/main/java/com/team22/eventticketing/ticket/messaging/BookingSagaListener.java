package com.team22.eventticketing.ticket.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team22.eventticketing.ticket.config.RabbitConfig;
import com.team22.eventticketing.ticket.model.BookingEventMapping;
import com.team22.eventticketing.ticket.model.Ticket;
import com.team22.eventticketing.ticket.model.TicketStatus;
import com.team22.eventticketing.ticket.repository.BookingEventMappingRepository;
import com.team22.eventticketing.ticket.repository.TicketRepository;
import com.team22.eventticketing.ticket.service.CacheEvictionService;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.events.BookingPlacedEvent;
import com.team22.eventticketing.contracts.events.TicketCancelledEvent;
import com.team22.eventticketing.contracts.events.TicketStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class BookingSagaListener {

    private static final Logger log = LoggerFactory.getLogger(BookingSagaListener.class);

    private static final Set<TicketStatus> CANCELLABLE = EnumSet.of(TicketStatus.VALID);

    private final TicketRepository ticketRepository;
    private final TicketEventPublisher publisher;
    private final CacheEvictionService cacheEviction;
    private final ObjectMapper objectMapper;
    private final BookingEventMappingRepository mappingRepository;

    public BookingSagaListener(TicketRepository ticketRepository,
                               TicketEventPublisher publisher,
                               CacheEvictionService cacheEviction,
                               ObjectMapper objectMapper,
                               BookingEventMappingRepository mappingRepository) {
        this.ticketRepository = ticketRepository;
        this.publisher = publisher;
        this.cacheEviction = cacheEviction;
        this.objectMapper = objectMapper;
        this.mappingRepository = mappingRepository;
    }

    @RabbitListener(queues = RabbitConfig.SAGA_QUEUE)
    public void onBookingEvent(Message message) throws Exception {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        byte[] body = message.getBody();

        switch (routingKey) {
            case RabbitConfig.RK_BOOKING_PLACED -> handlePlaced(
                    objectMapper.readValue(body, BookingPlacedEvent.class));
            case RabbitConfig.RK_BOOKING_COMPLETED -> handleCompleted(
                    objectMapper.readValue(body, BookingCompletedEvent.class));
            case RabbitConfig.RK_BOOKING_CANCELLED -> handleCancelled(
                    objectMapper.readValue(body, BookingCancelledEvent.class));
            default -> log.warn("Unhandled routing key on saga queue: {}", routingKey);
        }
    }

    @Transactional
    void handlePlaced(BookingPlacedEvent event) {
        log.info("Captured booking.placed bookingId={} userId={} eventId={}",
                event.bookingId(), event.userId(), event.eventId());
        mappingRepository.save(new BookingEventMapping(
                event.bookingId(), event.eventId(), LocalDateTime.now()));
    }

    private void handleCompleted(BookingCompletedEvent event) {
        Long bookingId = event.bookingId();
        log.info("Received booking.completed bookingId={} userId={} eventId={} totalAmount={}",
                bookingId, event.userId(), event.eventId(), event.totalAmount());

        List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
        if (tickets.isEmpty()) {
            log.warn("booking.completed: no tickets found for bookingId={}", bookingId);
            return;
        }

        for (Ticket ticket : tickets) {
            publisher.publishTicketStatusChanged(
                    new TicketStatusChangedEvent(ticket.getId(), bookingId, ticket.getStatus().name())
            );
        }
        log.info("booking.completed: emitted {} ticket.status-changed audit events for bookingId={}",
                tickets.size(), bookingId);
    }

    @Transactional
    void handleCancelled(BookingCancelledEvent event) {
        Long bookingId = event.bookingId();
        log.info("Received booking.cancelled bookingId={} reason={}", bookingId, event.reason());

        List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
        if (tickets.isEmpty()) {
            log.warn("booking.cancelled: no tickets found for bookingId={}", bookingId);
            return;
        }

        int cancelledCount = 0;
        for (Ticket ticket : tickets) {
            if (!CANCELLABLE.contains(ticket.getStatus())) continue;

            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);
            cacheEviction.evictDetail("ticket", ticket.getId());

            publisher.publishTicketCancelled(
                    new TicketCancelledEvent(ticket.getId(), bookingId)
            );
            cancelledCount++;
        }

        if (cancelledCount > 0) {
            cacheEviction.evictPattern("S4-F1");
            cacheEviction.evictPattern("S4-F6");
            cacheEviction.evictPattern("S4-F8");
            cacheEviction.evictPattern("S4-F9");
            cacheEviction.evictPattern("S4-F10");
        }

        log.info("booking.cancelled: cancelled {} of {} tickets for bookingId={}",
                cancelledCount, tickets.size(), bookingId);
    }
}
