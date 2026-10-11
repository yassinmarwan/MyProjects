package com.team22.eventticketing.booking.controller;

import com.team22.eventticketing.booking.dto.*;
import com.team22.eventticketing.booking.model.Booking;
import com.team22.eventticketing.booking.model.BookingStatus;
import com.team22.eventticketing.booking.security.AuthContext;
import com.team22.eventticketing.booking.service.BookingService;
import com.team22.eventticketing.booking.repository.BookingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.team22.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team22.eventticketing.contracts.dto.EventBookingRevenueDTO;

// adding the loggers

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/bookings") // Requirement 4.a
public class bookingController { // FIXED: Capitalized class name to match Java conventions
    // adding an instance of the logger
    private static final Logger log = LoggerFactory.getLogger(bookingController.class);
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    // FIXED: Constructor name must exactly match the class name
    public bookingController(BookingService bookingService, BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
    }

    private void assertOwnerOrAdmin(Long bookingUserId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        Long callerId = (Long) auth.getDetails();
        if (!callerId.equals(bookingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    @GetMapping("/health")
    public String statusCheck(){
        log.info("Received {} {}", "GET", "/api/bookings/health");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/health");

        return "OK";
    }

    @GetMapping("/search")
    public ResponseEntity<List<Booking>> searchBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Received {} {}", "GET", "/api/bookings/search");


        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end   = (endDate != null) ? endDate.atTime(23, 59, 59) : null;

        // FIXED: Removed the leftover debugging System.out.println from the merge

        List<Booking> results = bookingService.searchBookings(status, start, end);
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/search");

        return ResponseEntity.ok(results);
    }

    @PutMapping("/{bookingId}/confirm")
    public ResponseEntity<Booking> confirmBooking(
            @PathVariable Long bookingId,
            @RequestParam Long eventId) {
        log.info("Received {} {}", "PUT", "/api/bookings/" + bookingId + "/confirm");

        Booking confirmedBooking = bookingService.confirmBooking(bookingId, eventId);
        log.info("Returning {} for {} {}", 200, "PUT", "/api/bookings/" + bookingId + "/confirm");

        return ResponseEntity.ok(confirmedBooking);
    }

    @PostMapping("/estimate")
    public ResponseEntity<BookingCostEstimateDTO> getBookingCostEstimate(@RequestBody CostEstimateRequest request) {
        log.info("Received {} {}", "POST", "/api/bookings/estimate");

        BookingCostEstimateDTO estimate = bookingService.estimateCost(
                request.getEventId(),
                request.getTicketCount(),
                request.getTicketTier()
        );
        log.info("Returning {} for {} {}", 200, "POST", "/api/bookings/estimate");

        return ResponseEntity.ok(estimate);
    }

    // ─── Create ──────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Booking> create(@RequestBody Booking booking) {
        log.info("Received {} {}", "POST", "/api/bookings");
        log.info("Returning {} for {} {}", 201, "POST", "/api/bookings");

        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(booking));
    }

    // ─── Read by ID ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<Booking> findById(@PathVariable Long id) {
        log.info("Received {} {}", "GET", "/api/bookings/" + id);

        Booking booking = bookingService.findById(id);
        assertOwnerOrAdmin(booking.getUserId());
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/" + id);

        return ResponseEntity.ok(booking);
    }

    // ─── Read all (optionally filtered by userId) ─────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Booking>> findAll(
            @RequestParam(required = false) Long userId) {
        log.info("Received {} {}", "GET", "/api/bookings");

        if (userId != null) {
            return ResponseEntity.ok(bookingService.findByUserId(userId));
        }
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings");

        return ResponseEntity.ok(bookingService.findAll());
    }

    // ─── Update ───────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<Booking> update(
            @PathVariable Long id,
            @RequestBody Booking patch) {
        log.info("Received {} {}", "PUT", "/api/bookings/" + id);

        assertOwnerOrAdmin(bookingService.findById(id).getUserId());
        log.info("Returning {} for {} {}", 200, "PUT", "/api/bookings/" + id);

        return ResponseEntity.ok(bookingService.update(id, patch));
    }

    // ─── Delete ───────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Received {} {}", "DELETE", "/api/bookings/" + id);

        assertOwnerOrAdmin(bookingService.findById(id).getUserId());
        bookingService.delete(id);
        log.info("Returning {} for {} {}", 204, "DELETE", "/api/bookings/" + id);

        return ResponseEntity.noContent().build();
    }

    // ─── S3-F4: Complete Booking ──────────────────────────────────────────────────
    @PutMapping("/{id}/complete")
    public ResponseEntity<Booking> completeBooking(@PathVariable Long id) {
        log.info("Received {} {}", "PUT", "/api/bookings/" + id + "/complete");

        assertOwnerOrAdmin(bookingService.findById(id).getUserId());
        log.info("Returning {} for {} {}", 200, "PUT", "/api/bookings/" + id + "/complete");

        return ResponseEntity.ok(bookingService.completeBooking(id));
    }

    // ─── S3-F5: Filter by Metadata ────────────────────────────────────────────────
    @GetMapping("/metadata/search")
    public ResponseEntity<List<Booking>> searchByMetadata(
            @RequestParam String key,
            @RequestParam String value) {
        log.info("Received {} {}", "GET", "/api/bookings/metadata/search");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/metadata/search");

        return ResponseEntity.ok(bookingService.filterByMetadata(key, value));
    }

    // ─── S3-F6: Booking Analytics ─────────────────────────────────────────────────
    @GetMapping("/analytics")
    public ResponseEntity<BookingAnalyticsDTO> getAnalytics(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        log.info("Received {} {}", "GET", "/api/bookings/analytics");

        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end   = LocalDateTime.parse(endDate + "T23:59:59");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/analytics");

        return ResponseEntity.ok(bookingService.getAnalytics(start, end));
    }


    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long id) {
        log.info("Received {} {}", "PUT", "/api/bookings/" + id + "/cancel");

        assertOwnerOrAdmin(bookingService.findById(id).getUserId());
        bookingService.cancelBooking(id);
        log.info("Returning {} for {} {}", 200, "PUT", "/api/bookings/" + id + "/cancel");

        return ResponseEntity.ok().build();
    }

    // ─── Add Items to Booking ─────────────────────────────────────────────────────
    @PostMapping("/{bookingId}/items")
    public ResponseEntity<Booking> addItemsToBooking(
            @PathVariable Long bookingId,
            @RequestBody List<BookingItemRequest> requests) {
        log.info("Received {} {}", "POST", "/api/bookings/" + bookingId + "/items");


        assertOwnerOrAdmin(bookingService.findById(bookingId).getUserId());
        Booking updatedBooking = bookingService.addItemsToBooking(bookingId, requests);
        log.info("Returning {} for {} {}", 200, "POST", "/api/bookings/" + bookingId + "/items");

        return ResponseEntity.ok(updatedBooking);
    }

    // ─── Get Booking Details ──────────────────────────────────────────────────────
    @GetMapping("/{id}/details")
    public ResponseEntity<BookingDetailsDTO> getBookingDetails(@PathVariable Long id) {
        log.info("Received {} {}", "GET", "/api/bookings/" + id + "/details");

        assertOwnerOrAdmin(bookingService.findById(id).getUserId());
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/" + id + "/details");

        return ResponseEntity.ok(bookingService.getBookingDetails(id));
    }

    // ─── Milestone 2: Record Attendance ───────────────────────────────────────────
    @PostMapping("/{bookingId}/record-attendance")
    public ResponseEntity<?> recordAttendance(@PathVariable Long bookingId) {
        log.info("Received {} {}", "POST", "/api/bookings/" + bookingId + "/record-attendance");

        // 1. Fetch from PostgreSQL directly
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with id: " + bookingId));

        // 2. Security: Verify that the user owns this booking or is an ADMIN.
        assertOwnerOrAdmin(booking.getUserId());

        // 3. Delegate the complex polyglot logic to the Service layer
        bookingService.recordAttendance(bookingId);
        log.info("Returning {} for {} {}", 200, "POST", "/api/bookings/" + bookingId + "/record-attendance");

        // 4. Return standard success response
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Attendance recorded successfully"
        ));
    }

    // ─── Milestone 2: Analytics Dashboard ─────────────────────────────────────────
    @GetMapping("/analytics/dashboard")
    public ResponseEntity<BookingAnalyticsDashboardDTO> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Received {} {}", "GET", "/api/bookings/analytics/dashboard");

        // Validation
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date");
        }

        // Date Expansion [00:00:00 to 23:59:59.999]
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end   = (endDate != null) ? endDate.atTime(23, 59, 59) : null;

        BookingAnalyticsDashboardDTO dashboard = bookingService.getDashboard(start, end);
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/analytics/dashboard");

        return ResponseEntity.ok(dashboard);
    }

    // ─── Milestone 2: Recommendations ─────────────────────────────────────────────
    @GetMapping("/recommendations")
    public ResponseEntity<List<EventRecommendationDTO>> getRecommendations(
            @RequestParam Long userId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        log.info("Received {} {}", "GET", "/api/bookings/recommendations");

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long callerId = (Long) auth.getDetails();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("ATTENDEE");

        AuthContext ctx = new AuthContext(request, "USER");
        ctx.setUserId(callerId);
        ctx.setUserRole(role);
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/recommendations");

        return ResponseEntity.ok(bookingService.getRecommendations(userId, limit, ctx));
    }


    // new endpoints that are going to be called by other services thro feign requests
    // ─── M3: Endpoints Exposed for Other Services ─────────────────────────────────

    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<BookingSummaryDTO> getUserBookingSummary(@PathVariable Long userId) {
        log.info("Received {} {}", "GET", "/api/bookings/user/" + userId + "/summary");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/user/" + userId + "/summary");

        return ResponseEntity.ok(bookingService.getUserBookingSummary(userId));
    }

    @GetMapping("/user/{userId}/active-count")
    public ResponseEntity<Integer> getUserActiveBookingCount(@PathVariable Long userId) {
        log.info("Received {} {}", "GET", "/api/bookings/user/" + userId + "/active-count");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/user/" + userId + "/active-count");

        return ResponseEntity.ok(bookingService.getUserActiveBookingCount(userId));
    }

    @GetMapping("/user/{userId}/count")
    public ResponseEntity<Long> getUserBookingCount(
            @PathVariable Long userId,
            @RequestParam(required = false) String status) {
        log.info("Received {} {}", "GET", "/api/bookings/user/" + userId + "/count");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/user/" + userId + "/count");

        return ResponseEntity.ok(bookingService.getUserBookingCount(userId, status));
    }

    @GetMapping("/user/{userId}/total")
    public ResponseEntity<BigDecimal> getUserBookingTotal(
            @PathVariable Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        log.info("Received {} {}", "GET", "/api/bookings/user/" + userId + "/total");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/user/" + userId + "/total");

        return ResponseEntity.ok(bookingService.getUserBookingTotal(userId, startDate, endDate));
    }

    @GetMapping("/event/{eventId}/active-count")
    public ResponseEntity<Integer> getEventActiveBookingCount(@PathVariable Long eventId) {
        log.info("Received {} {}", "GET", "/api/bookings/event/" + eventId + "/active-count");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/event/" + eventId + "/active-count");

        return ResponseEntity.ok(bookingService.getEventActiveBookingCount(eventId));
    }

    @GetMapping("/event/{eventId}/revenue")
    public ResponseEntity<EventBookingRevenueDTO> getEventRevenue(
            @PathVariable Long eventId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        log.info("Received {} {}", "GET", "/api/bookings/event/" + eventId + "/revenue");
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/event/" + eventId + "/revenue");
        return ResponseEntity.ok(bookingService.getEventRevenue(eventId, startDate, endDate));
    }

    @GetMapping("/{bookingId}/items")
    public ResponseEntity<List<BookingItemDTO>> getBookingItems(@PathVariable Long bookingId) {
        log.info("Received {} {}", "GET", "/api/bookings/" + bookingId + "/items");
        assertOwnerOrAdmin(bookingService.findById(bookingId).getUserId());
        log.info("Returning {} for {} {}", 200, "GET", "/api/bookings/" + bookingId + "/items");
        return ResponseEntity.ok(bookingService.getBookingItemDTOs(bookingId));
    }
}