package com.team22.eventticketing.user.config;

import org.springframework.context.annotation.Configuration;

import com.team22.eventticketing.user.observer.MongoEventLogger;
import com.team22.eventticketing.user.service.AuthService;
import com.team22.eventticketing.user.service.FavoriteVenueService;
import com.team22.eventticketing.user.service.UserService;

import jakarta.annotation.PostConstruct;



@Configuration
public class ObserverConfig {

    private final UserService userService;
    private final FavoriteVenueService favoriteVenueService;
    private final MongoEventLogger mongoEventLogger;
    private final AuthService authService;

    public ObserverConfig(UserService userService,
                          FavoriteVenueService favoriteVenueService,
                          MongoEventLogger mongoEventLogger,
                          AuthService authService) {
        this.userService = userService;
        this.favoriteVenueService = favoriteVenueService;
        this.mongoEventLogger = mongoEventLogger;
        this.authService = authService;
    }

    @PostConstruct
    public void registerObservers() {
        userService.register(mongoEventLogger);
        favoriteVenueService.register(mongoEventLogger);
        authService.register(mongoEventLogger);
    }
}
