package com.team22.eventticketing.event.controller;

import com.team22.eventticketing.event.dto.EventRatingRequestDTO;
import com.team22.eventticketing.event.dto.EventRevenueDTO;
import com.team22.eventticketing.event.dto.EventSessionAlertDTO;
import com.team22.eventticketing.event.dto.TopEventDTO;
import com.team22.eventticketing.event.dto.UpdateEventStatusRequest;
import com.team22.eventticketing.event.dto.VerifySessionRequestDTO;
import com.team22.eventticketing.event.model.Event;
import com.team22.eventticketing.event.model.EventCategory;
import com.team22.eventticketing.event.model.EventSearchDocument;
import com.team22.eventticketing.event.model.EventSession;
import com.team22.eventticketing.event.service.EventFullTextSearchService;
import com.team22.eventticketing.event.service.EventService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.team22.eventticketing.event.dto.EventDashboardDTO;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.team22.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team22.eventticketing.contracts.dto.VenueCoordsDTO;

@RestController
@RequestMapping("/api/events")
public class eventController {

    private final EventService eventService;
    private final EventFullTextSearchService eventFullTextSearchService;

    public eventController(EventService eventService,
                           EventFullTextSearchService eventFullTextSearchService) {
        this.eventService = eventService;
        this.eventFullTextSearchService = eventFullTextSearchService;
    }

    @GetMapping("/health")
    public String statusCheck() {
        return "OK";
    }

    // [S2-F4] Update Event Status
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateEventStatus(
            @PathVariable Long id,
            @RequestBody UpdateEventStatusRequest request
    ) {
        eventService.updateEventStatus(id, request);
        return ResponseEntity.ok().build();
    }

    // [S2-F5] Filter Events by Detail
    @GetMapping("/details/search")
    public ResponseEntity<List<Event>> filterEvents(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(eventService.filterEventsByDetail(key, value, status));
    }

    // [S2-F7] Rate an Event
    @PostMapping("/{id}/rate")
    public ResponseEntity<Void> rateEvent(@PathVariable Long id, @RequestBody EventRatingRequestDTO request) {
        eventService.rateEvent(id, request.bookingId(), request.rating());
        return ResponseEntity.ok().build();
    }

    // [S2-F2] Update Event Details (JSONB Merge)
    @PutMapping("/{id}/details")
    public ResponseEntity<Event> updateEventDetails(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request
    ) {
        Event updatedEvent = eventService.updateEventDetails(id, request);
        return ResponseEntity.ok(updatedEvent);
    }

    // [S2-F8] Verify Event Session
    @PutMapping("/{eventId}/sessions/{sessionId}/verify")
    public ResponseEntity<Event> verifySession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody VerifySessionRequestDTO request
    ) {
        Event updatedEvent = eventService.verifyEventSession(eventId, sessionId, request.verifiedBy());
        return ResponseEntity.ok(updatedEvent);
    }

    // [S2-F10] Full-Text Event Search
    @GetMapping("/search/full-text")
    public ResponseEntity<List<EventSearchDocument>> fullTextSearchEvents(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating
    ) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (startDate != null && !startDate.isBlank()) {
            start = LocalDateTime.parse(startDate + "T00:00:00");
        }

        if (endDate != null && !endDate.isBlank()) {
            end = LocalDateTime.parse(endDate + "T23:59:59");
        }

        return ResponseEntity.ok(
                eventFullTextSearchService.fullTextSearch(
                        query,
                        category,
                        venue,
                        status,
                        start,
                        end,
                        minRating,
                        maxRating
                )
        );
    }

    // [S2-F1] Search Events
    @GetMapping("/search")
    public ResponseEntity<List<Event>> searchEvents(
            @RequestParam(required = false) EventCategory category,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (startDate != null && !startDate.isBlank()) {
            start = LocalDateTime.parse(startDate + "T00:00:00");
        }

        if (endDate != null && !endDate.isBlank()) {
            end = LocalDateTime.parse(endDate + "T23:59:59");
        }

        return ResponseEntity.ok(eventService.searchEvents(category, start, end));
    }

    // [S2-F3] Get Event Revenue
    @GetMapping("/{id}/revenue")
    public ResponseEntity<EventRevenueDTO> getRevenue(
            @PathVariable Long id,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");

        return ResponseEntity.ok(eventService.getEventRevenue(id, start, end));
    }

    // [S2-F9] Get Events with Unverified Sessions Report
    @GetMapping("/sessions/unverified")
    public ResponseEntity<List<EventSessionAlertDTO>> getUnverifiedSessions() {
        return ResponseEntity.ok(eventService.getUnverifiedSessionsReport());
    }

    // [S2-F6] Top Rated Events
    @GetMapping("/reports/top-rated")
    public ResponseEntity<List<TopEventDTO>> getTopRatedEvents(@RequestParam int limit) {
        return ResponseEntity.ok(eventService.getTopRatedEvents(limit));
    }

    // CRUD ENDPOINTS: EVENT

    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {
        Event createdEvent = eventService.createEvent(event);
        return new ResponseEntity<>(createdEvent, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Event> updateEvent(@PathVariable Long id, @RequestBody Event updatedEvent) {
        Event savedEvent = eventService.updateEvent(id, updatedEvent);
        return ResponseEntity.ok(savedEvent);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    // [M3 S2-READ-DB] Average session capacity for Feign consumers
    @GetMapping("/{id}/sessions/avg-capacity")
    public ResponseEntity<AvgCapacityDTO> getAverageSessionCapacity(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getAverageSessionCapacity(id));
    }

    // [M3 S2-READ-DB] Venue coordinates for Feign consumers
    @GetMapping("/{id}/venue-coords")
    public ResponseEntity<VenueCoordsDTO> getVenueCoordinates(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getVenueCoordinates(id));
    }

    // CRUD ENDPOINTS: EVENT SESSION

    @PostMapping("/{eventId}/sessions")
    public ResponseEntity<EventSession> createEventSession(
            @PathVariable Long eventId,
            @RequestBody EventSession session
    ) {
        EventSession createdSession = eventService.createEventSession(eventId, session);
        return new ResponseEntity<>(createdSession, HttpStatus.CREATED);
    }

    @GetMapping("/{eventId}/sessions")
    public ResponseEntity<List<EventSession>> getSessionsForEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getSessionsForEvent(eventId));
    }

    @GetMapping("/{eventId}/sessions/{sessionId}")
    public ResponseEntity<EventSession> getEventSessionById(
            @PathVariable Long eventId,
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(eventService.getEventSessionById(eventId, sessionId));
    }

    @PutMapping("/{eventId}/sessions/{sessionId}")
    public ResponseEntity<EventSession> updateEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody EventSession updatedSession
    ) {
        return ResponseEntity.ok(eventService.updateEventSession(eventId, sessionId, updatedSession));
    }

    @DeleteMapping("/{eventId}/sessions/{sessionId}")
    public ResponseEntity<Void> deleteEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId
    ) {
        eventService.deleteEventSession(eventId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostConstruct
    public void init() {
        System.out.println("EVENT CONTROLLER LOADED");
    }

    // [S2-F11] Index Event for Search

    @PostMapping("/{id}/index")
    public ResponseEntity<Void> indexEventExplicitly(@PathVariable Long id) {
        eventService.explicitIndexEvent(id);
        return ResponseEntity.ok().build();
    }

    // [S2-F12] Event Performance Dashboard
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<EventDashboardDTO> getDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventDashboard(id));
    }
}