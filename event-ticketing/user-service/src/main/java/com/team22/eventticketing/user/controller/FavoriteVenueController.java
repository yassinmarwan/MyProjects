package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.user.entity.FavoriteVenue;
import com.team22.eventticketing.user.service.FavoriteVenueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorite-venues")
public class FavoriteVenueController {

    private final FavoriteVenueService favoriteVenueService;

    public FavoriteVenueController(FavoriteVenueService favoriteVenueService) {
        this.favoriteVenueService = favoriteVenueService;
    }

    // ─── Create ──────────────────────────────────────────────────────────────────

    /**
     * POST /api/favorite-venues?userId={userId}
     * Creates a FavoriteVenue linked to the given user.
     */
    @PostMapping
    public ResponseEntity<FavoriteVenue> create(
            @RequestParam Long userId,
            @RequestBody FavoriteVenue favoriteVenue) {
        FavoriteVenue created = favoriteVenueService.create(userId, favoriteVenue);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ─── Read by ID ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<FavoriteVenue> findById(@PathVariable Long id) {
        return ResponseEntity.ok(favoriteVenueService.findById(id));
    }

    // ─── Read all (optionally filtered by userId) ─────────────────────────────────

    /**
     * GET /api/favorite-venues           → all favorite venues
     * GET /api/favorite-venues?userId=1  → all for a specific user
     */
    @GetMapping
    public ResponseEntity<List<FavoriteVenue>> findAll(
            @RequestParam(required = false) Long userId) {
        if (userId != null) {
            return ResponseEntity.ok(favoriteVenueService.findByUserId(userId));
        }
        return ResponseEntity.ok(favoriteVenueService.findAll());
    }

    // ─── Update ───────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<FavoriteVenue> update(
            @PathVariable Long id,
            @RequestBody FavoriteVenue patch) {
        return ResponseEntity.ok(favoriteVenueService.update(id, patch));
    }

    // ─── Delete ───────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        favoriteVenueService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
