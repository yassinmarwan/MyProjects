package com.team22.eventticketing.ticket.config;

import com.team22.eventticketing.ticket.observer.MongoEventLogger;
import com.team22.eventticketing.ticket.service.TicketService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObserverConfig {

    private final TicketService ticketService;
    private final MongoEventLogger mongoEventLogger;

    public ObserverConfig(TicketService ticketService, MongoEventLogger mongoEventLogger) {
        this.ticketService = ticketService;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void registerObservers() {
        ticketService.register(mongoEventLogger);
    }
}
