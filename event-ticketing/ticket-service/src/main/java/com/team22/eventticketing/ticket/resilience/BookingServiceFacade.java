package com.team22.eventticketing.ticket.resilience;

import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BookingServiceFacade {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceFacade.class);

    private final BookingServiceClient bookingServiceClient;

    public BookingServiceFacade(BookingServiceClient bookingServiceClient) {
        this.bookingServiceClient = bookingServiceClient;
    }

    @CircuitBreaker(name = "bookingService", fallbackMethod = "getBookingFallback")
    public BookingDTO getBooking(Long bookingId) {
        return bookingServiceClient.getBooking(bookingId);
    }

    @SuppressWarnings("unused")
    private BookingDTO getBookingFallback(Long bookingId, Throwable t) {
        log.warn("Circuit breaker fallback for getBooking(bookingId={}): {}", bookingId, t.toString());
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "booking-service unavailable (circuit breaker open)");
    }
}
