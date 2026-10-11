package com.team22.eventticketing.contracts.feign;

import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team22.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team22.eventticketing.contracts.dto.BookingItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {
    @GetMapping("/api/bookings/{id}")
    BookingDTO getBooking(@PathVariable("id") Long id);

    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getActiveBookingCount(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/user/{userId}/count")
    long getTotalBookingCount(@PathVariable("userId") Long userId, @RequestParam(value = "status", required = false) String status);

    @GetMapping("/api/bookings/user/{userId}/total")
    BigDecimal getUserBookingTotal(@PathVariable("userId") Long userId, @RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate);

    @GetMapping("/api/bookings/event/{eventId}/revenue")
    EventBookingRevenueDTO getEventRevenue(@PathVariable("eventId") Long eventId, @RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate);

    @GetMapping("/api/bookings/event/{eventId}/active-count")
    int getEventActiveBookingCount(@PathVariable("eventId") Long eventId);

    @GetMapping("/api/bookings/{bookingId}/items")
    List<BookingItemDTO> getBookingItems(@PathVariable("bookingId") Long bookingId);




}