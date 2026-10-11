package com.team22.eventticketing.ticket.controller;

import com.team22.eventticketing.ticket.model.dto.*;
import com.team22.eventticketing.contracts.dto.EventTicketSummaryDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.team22.eventticketing.ticket.model.Ticket;
import com.team22.eventticketing.ticket.service.TicketScanService;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.team22.eventticketing.ticket.service.TicketService;
import com.team22.eventticketing.ticket.observer.MongoEventLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

@RestController
@RequestMapping("/api/tickets")
public class ticketController {

    private final TicketService ticketService;
    private final MongoEventLogger eventLogger;
    private final TicketScanService ticketScanService;
    private static final Logger log = LoggerFactory.getLogger(ticketController.class);

    public ticketController(TicketService ticketService, MongoEventLogger eventLogger,
                            TicketScanService ticketScanService) {
        this.ticketService = ticketService;
        this.eventLogger = eventLogger;
        this.ticketScanService = ticketScanService;
    }

    // [S4-F1]
    @GetMapping("/booking/{bookingId}/latest")
    public Ticket getLatestTicket(@PathVariable Long bookingId) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/booking/" + bookingId + "/latest"
        );

        Ticket response =
                ticketService.getLatestTicketByBooking(bookingId);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/booking/" + bookingId + "/latest"
        );

        return response;
    }

    @PostMapping("/booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket issueTicket(
            @PathVariable Long bookingId,
            @RequestBody Map<String, Object> request) {

        log.info(
                "Received {} {}",
                "POST",
                "/api/tickets/booking/" + bookingId
        );

        String attendeeName = (String) request.get("attendeeName");
        String ticketCode = (String) request.get("ticketCode");
        Map<String, Object> metadata =
                (Map<String, Object>) request.get("metadata");

        Ticket response = ticketService.issueTicket(
                bookingId,
                attendeeName,
                ticketCode,
                metadata
        );

        log.info(
                "Returning {} for {} {}",
                201,
                "POST",
                "/api/tickets/booking/" + bookingId
        );

        return response;
    }

    @GetMapping("/nearby")
    public List<NearbyTicketDTO> getNearbyTickets(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam double radiusKm) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/nearby"
        );

        List<NearbyTicketDTO> response =
                ticketService.findNearbyTickets(
                        lat,
                        lon,
                        radiusKm
                );

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/nearby"
        );

        return response;
    }

    @GetMapping("/health")
    public String statusCheck() {
        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/health"
        );

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/health"
        );

        return "OK";
    }

    // ==================== CRUD ====================

    @PostMapping
    public ResponseEntity<Ticket> createTicket(@RequestBody Ticket ticket) {
        log.info(
                "Received {} {}",
                "POST",
                "/api/tickets"
        );

        Ticket created = ticketService.createTicket(ticket);

        log.info(
                "Returning {} for {} {}",
                201,
                "POST",
                "/api/tickets"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public Ticket getTicketById(@PathVariable Long id) {
        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/" + id
        );

        Ticket response = ticketService.getTicketById(id);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/" + id
        );

        return response;
    }

    @GetMapping
    public List<Ticket> getAllTickets() {
        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets"
        );

        List<Ticket> response = ticketService.getAllTickets();

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets"
        );

        return response;
    }

    @PutMapping("/{id}")
    public Ticket updateTicket(@PathVariable Long id, @RequestBody Ticket ticket) {
        log.info(
                "Received {} {}",
                "PUT",
                "/api/tickets/" + id
        );

        Ticket response = ticketService.updateTicket(id, ticket);

        log.info(
                "Returning {} for {} {}",
                200,
                "PUT",
                "/api/tickets/" + id
        );

        return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        log.info(
                "Received {} {}",
                "DELETE",
                "/api/tickets/" + id
        );

        ticketService.deleteTicket(id);

        log.info(
                "Returning {} for {} {}",
                200,
                "DELETE",
                "/api/tickets/" + id
        );

        return ResponseEntity.ok().build();
    }

    // ==================== S4-F4: Batch Ticket Issuance ====================

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Integer>> batchIssueTickets(
            @RequestBody BatchTicketRequestDTO request
    ) {

        log.info(
                "Received {} {}",
                "POST",
                "/api/tickets/batch"
        );

        int count =
                ticketService.batchIssueTickets(request);

        log.info(
                "Returning {} for {} {}",
                201,
                "POST",
                "/api/tickets/batch"
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("count", count));
    }

    // ==================== S4-F7: Purge Expired Tickets ====================

    @DeleteMapping("/purge")
    public Map<String, Object> purgeTickets(@RequestParam("olderThanDays") int olderThanDays) {
        log.info(
                "Received {} {}",
                "DELETE",
                "/api/tickets/purge"
        );

        if (olderThanDays < 0) {
            log.warn(
                    "INVALID_PURGE_REQUEST olderThanDays={}",
                    olderThanDays
            );

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "olderThanDays must be non-negative");
        }

        int count = ticketService.purgeExpiredTickets(olderThanDays);

        Map<String, Object> response = new HashMap<>();
        response.put("deletedCount", count);

        log.info(
                "Returning {} for {} {}",
                200,
                "DELETE",
                "/api/tickets/purge"
        );

        return response;
    }

    // ==================== S4-F8: Event Attendance Summary ====================

//    @GetMapping("/event/{eventId}/summary")
//    public EventAttendanceSummaryDTO getEventAttendanceSummary(@PathVariable Long eventId) {
//        return ticketService.getSummary(eventId);
//    }

    // ==================== S4-F9: Unused Upcoming Tickets ====================

    @GetMapping("/unused-upcoming")
    public List<UnusedTicketDTO> getUnusedUpcomingTickets() {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/unused-upcoming"
        );

        List<UnusedTicketDTO> response =
                ticketService.getUnusedUpcomingTickets();

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/unused-upcoming"
        );

        return response;
    }
    // ==================== S4-F5: Filter Tickets by Metadata ====================

    @GetMapping("/metadata/search")
    public List<Ticket> filterTicketsByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/metadata/search"
        );

        List<Ticket> response =
                ticketService.filterByMetadata(key, operator, value);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/metadata/search"
        );

        return response;
    }

    // ==================== S4-F6: Get Tickets in Date Range ====================

    @GetMapping("/history")
    public List<Ticket> getTicketHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/history"
        );

        List<Ticket> response =
                ticketService.getTicketHistory(startDate, endDate, status);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/history"
        );

        return response;
    }

    // S4-F10
    @GetMapping("/analytics")
    public ResponseEntity<TicketAnalyticsDTO> getTicketAnalyticsDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/analytics"
        );

        // STEP 4: Trigger the Observer Pattern for Observability logging.
        // Doing this HERE ensures it runs on every invocation, bypassing the Redis
        // cache[cite: 1].
        Map<String, Object> payload = new HashMap<>();
        payload.put("startDate", startDate);
        payload.put("endDate", endDate);

        // Log the purely observability action to MongoDB via the Observer chain[cite:
        // 1]
        eventLogger.onEvent("ANALYTICS_VIEWED", payload);

        // STEP 5: Call the cached Service method and return a 200 OK[cite: 1]
        TicketAnalyticsDTO response = ticketService.getAnalytics(startDate, endDate);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/analytics"
        );

        return ResponseEntity.ok(response);
    }

    // S4-F11
    @PostMapping("/{id}/scan")
    public ResponseEntity<?> scanTicket(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody TicketScanRequestDTO request) {

        log.info(
                "Received {} {}",
                "POST",
                "/api/tickets/" + id + "/scan"
        );

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn(
                    "INVALID_SCAN_AUTH ticketId={}",
                    id
            );

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid token");
        }

        String token = authHeader.substring(7);

        ticketScanService.recordScan(id, token, request);

        log.info(
                "Returning {} for {} {}",
                201,
                "POST",
                "/api/tickets/" + id + "/scan"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body("Scan event recorded");
    }

    // S4-F12
    @GetMapping("/{id}/scans")
    public ResponseEntity<List<TicketScanDTO>> getScanHistory(
            @PathVariable Long id,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/" + id + "/scans"
        );

        Instant start = startTime != null ? Instant.parse(startTime) : null;
        Instant end = endTime != null ? Instant.parse(endTime) : null;

        List<TicketScanDTO> response =
                ticketScanService.getScanHistory(id, start, end);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/" + id + "/scans"
        );

        return ResponseEntity.ok(response);
    }

    // new first endpoint
    @GetMapping("/booking/{bookingId}/used-count")
    public int getUsedTicketCountForBooking(
            @PathVariable Long bookingId
    ) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/booking/" + bookingId + "/used-count"
        );

        int response =
                ticketService.getUsedTicketCountForBooking(bookingId);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/booking/" + bookingId + "/used-count"
        );

        return response;
    }

    // new second endpoint
    @GetMapping("/event/{eventId}/summary")
    public EventTicketSummaryDTO getEventTicketSummary(
            @PathVariable Long eventId
    ) {

        log.info(
                "Received {} {}",
                "GET",
                "/api/tickets/event/" + eventId + "/summary"
        );

        EventTicketSummaryDTO response =
                ticketService.getEventTicketSummary(eventId);

        log.info(
                "Returning {} for {} {}",
                200,
                "GET",
                "/api/tickets/event/" + eventId + "/summary"
        );

        return response;
    }
}
