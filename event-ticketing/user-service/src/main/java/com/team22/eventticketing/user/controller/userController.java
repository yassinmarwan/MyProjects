package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.user.dto.ActivityFeedResponse;
import com.team22.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team22.eventticketing.user.dto.UserProfileDTO;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.service.FavoriteVenueService;
import com.team22.eventticketing.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class userController {

    private final UserService userService;
    private final FavoriteVenueService favoriteVenueService;

    public userController(UserService userService, FavoriteVenueService favoriteVenueService) {
        this.userService = userService;
        this.favoriteVenueService = favoriteVenueService;
    }

    private void assertOwnerOrAdmin(Long resourceId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;
        String email = (String) auth.getPrincipal();
        Long callerId = userService.getIdByEmail(email);
        if (!callerId.equals(resourceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    // ─── Create ──────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(user));
    }

    // ─── Read all ─────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<User>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

// ─── Read by ID ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<User> findById(@PathVariable Long id) {
        assertOwnerOrAdmin(id);
        return ResponseEntity.ok(userService.findById(id));
    }

// ─── Update ───────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<User> update(
            @PathVariable Long id,
            @RequestBody User patch) {
        assertOwnerOrAdmin(id);
        return ResponseEntity.ok(userService.update(id, patch));
    }

// ─── Delete ───────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assertOwnerOrAdmin(id);
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── [S1-F2] Update User Preferences ─────────────────────────────────────────

    @PutMapping("/{id}/preferences")
    public ResponseEntity<User> updatePreferences(
            @PathVariable Long id,
            @RequestBody Map<String, Object> preferences) {
        return ResponseEntity.ok(userService.updatePreferences(id, preferences));
    }

    // ─── Health ───────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public String statusCheck() {
        return "OK";
    }

    // ─── [S1-F3] Search Users ─────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<User>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Role role) {
        return ResponseEntity.ok(userService.search(name, email, role));
    }

    // ─── [S1-F5] Filter Users by Preference (JSONB Query) ────────────────────────

    @GetMapping("/preferences/search")
    public ResponseEntity<List<User>> findByPreference(
            @RequestParam String key,
            @RequestParam String value) {
        return ResponseEntity.ok(userService.findByPreference(key, value));
    }

    // ─── [S1-F4] Deactivate User Account ─────────────────────────────────────────

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<User> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(userService.deactivateUser(id));
    }

    // ─── [S1-F7] Set Default Venue ───────────────────────────────────────────────

    @PutMapping("/{userId}/venues/{venueId}/default")
    public ResponseEntity<User> setDefaultVenue(
            @PathVariable Long userId,
            @PathVariable Long venueId) {
        return ResponseEntity.ok(favoriteVenueService.setDefault(userId, venueId));
    }

    // ─── [S1-F3] Get User Booking Summary ────────────────────────────────────────

    @GetMapping("/{id}/booking-summary")
    public ResponseEntity<UserBookingSummaryDTO> getBookingSummary(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getBookingSummary(id));
    }

    // ─── [S1-F8] Get User Profile ────────────────────────────────────────────────

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDTO> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    // ─── [S1-F9] Filter by Favorite Category ─────────────────────────────────────

    @GetMapping("/preferences/category")
    public ResponseEntity<List<User>> findByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "0") int minBookings) {
        return ResponseEntity.ok(
                userService.findByFavoriteCategoryAndMinBookings(category, minBookings));
    }

    // ─── [CC-2] Update User Role (ADMIN only) ────────────────────────────────────
    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userService.updateRole(id, role));
    }

    // ─── [S1-F12] Get User Activity Feed ─────────────────────────────────────────

    @GetMapping("/{id}/activity")
    public ResponseEntity<ActivityFeedResponse> getActivityFeed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            Long callerId = (Long) auth.getDetails();
            if (!callerId.equals(id)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }

        userService.findById(id); // throws 404 if user not found

        return ResponseEntity.ok(userService.getActivityFeed(id, page, size));
    }
}
