package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.user.dto.TopAttendeeDTO;
import com.team22.eventticketing.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/reports")
public class UserReportController {

    private final UserService userService;

    public UserReportController(UserService userService) {
        this.userService = userService;
    }

    // S1-F6: top N attendees by total spending in a date range
    @GetMapping("/top-attendees")
    public ResponseEntity<List<TopAttendeeDTO>> getTopAttendees(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(userService.getTopAttendees(startDate, endDate, limit));
    }
}
