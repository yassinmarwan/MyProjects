package com.team22.eventticketing.event.config;

import com.team22.eventticketing.event.observer.MongoEventLogger;
import com.team22.eventticketing.event.service.EventService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObserverConfig {

    private final EventService eventService;
    private final MongoEventLogger mongoEventLogger;

    public ObserverConfig(EventService eventService, MongoEventLogger mongoEventLogger) {
        this.eventService = eventService;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void registerObservers() {
        eventService.register(mongoEventLogger);
    }
}
