package com.team22.eventticketing.booking.config;

import com.team22.eventticketing.booking.observer.MongoEventLogger;
import com.team22.eventticketing.booking.service.BookingService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObserverConfig {

    private final BookingService bookingService;
    private final MongoEventLogger mongoEventLogger;

    public ObserverConfig(BookingService bookingService, MongoEventLogger mongoEventLogger) {
        this.bookingService = bookingService;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void registerObservers() {
        bookingService.register(mongoEventLogger);
    }
}
