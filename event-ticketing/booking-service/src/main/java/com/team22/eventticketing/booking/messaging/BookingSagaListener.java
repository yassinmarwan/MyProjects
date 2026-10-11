package com.team22.eventticketing.booking.messaging;

import com.team22.eventticketing.booking.model.Booking;
import com.team22.eventticketing.booking.model.BookingStatus;
import com.team22.eventticketing.booking.repository.BookingRepository;
import com.team22.eventticketing.contracts.events.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingSagaListener {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public BookingSagaListener(BookingRepository bookingRepository, BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @RabbitListener(queues = "booking.saga-feedback")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // SAGA COMPENSATION: If payment fails, cancel the booking
        bookingRepository.findById(event.bookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            // Broadcast cancellation so Ticket service knows
            BookingCancelledEvent cancelEvent = new BookingCancelledEvent(
                    booking.getId(), booking.getUserId(), booking.getEventId(), "Payment Failed: " + event.reason());
            eventPublisher.publishBookingCancelled(cancelEvent);
        });
    }

    @Transactional
    @RabbitListener(queues = "booking.saga-feedback")
    public void handleTicketIssued(TicketIssuedEvent event) {
        // SUCCESS: If tickets are issued, the saga is done!
        bookingRepository.findById(event.bookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
        });
    }
}