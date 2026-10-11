package com.team22.eventticketing.user.service;

import com.team22.eventticketing.user.entity.FavoriteVenue;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.repository.FavoriteVenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteVenueServiceUnitTest {

    @Mock FavoriteVenueRepository favoriteVenueRepository;
    @Mock UserService userService;
    @Mock CacheEvictionService cacheEviction;

    FavoriteVenueService favoriteVenueService;

    @BeforeEach
    void setUp() {
        favoriteVenueService = new FavoriteVenueService(
                favoriteVenueRepository, userService, cacheEviction);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User testUser(Long id) {
        User u = new User("Alice", "alice@example.com", "hashed", "01012345678", Role.ATTENDEE);
        u.setId(id);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private FavoriteVenue venueForUser(Long venueId, Long userId) {
        User user = testUser(userId);
        FavoriteVenue v = new FavoriteVenue("My Spot", "Cairo Opera House", "Zamalek", user);
        v.setId(venueId);
        v.setIsDefault(false);
        return v;
    }

    // ══════════════════════════════════════════════════════════════════════
    // create
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("create: user found → venue saved with user association and ID returned")
    void create_happyPath_savesVenueWithUser() {
        User user = testUser(1L);
        when(userService.findById(1L)).thenReturn(user);
        FavoriteVenue venue = new FavoriteVenue("Home Spot", "Stadium", "Cairo", null);
        when(favoriteVenueRepository.save(any(FavoriteVenue.class))).thenAnswer(inv -> {
            FavoriteVenue v = inv.getArgument(0);
            v.setId(10L);
            return v;
        });

        FavoriteVenue result = favoriteVenueService.create(1L, venue);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUser()).isEqualTo(user);
        verify(favoriteVenueRepository).save(venue);
    }

    // ══════════════════════════════════════════════════════════════════════
    // findById
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("findById: venue exists → returns venue")
    void findById_found_returnsVenue() {
        FavoriteVenue venue = venueForUser(5L, 1L);
        when(favoriteVenueRepository.findById(5L)).thenReturn(Optional.of(venue));

        FavoriteVenue result = favoriteVenueService.findById(5L);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getVenueName()).isEqualTo("Cairo Opera House");
    }

    @Test
    @DisplayName("findById: venue not found → 404 NOT_FOUND")
    void findById_notFound_throws404() {
        when(favoriteVenueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteVenueService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FavoriteVenue not found");
    }

    // ══════════════════════════════════════════════════════════════════════
    // findByUserId
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("findByUserId: returns all venues for the given user")
    void findByUserId_returnsVenues() {
        User user = testUser(1L);
        when(userService.findById(1L)).thenReturn(user);
        FavoriteVenue v1 = venueForUser(1L, 1L);
        FavoriteVenue v2 = venueForUser(2L, 1L);
        when(favoriteVenueRepository.findByUserId(1L)).thenReturn(List.of(v1, v2));

        List<FavoriteVenue> result = favoriteVenueService.findByUserId(1L);

        assertThat(result).hasSize(2);
    }

    // ══════════════════════════════════════════════════════════════════════
    // update
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("update: applies patch, saves venue, evicts cache")
    void update_happyPath_savesAndEvictsCache() {
        FavoriteVenue existing = venueForUser(5L, 1L);
        when(favoriteVenueRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(favoriteVenueRepository.save(any(FavoriteVenue.class))).thenAnswer(inv -> inv.getArgument(0));

        FavoriteVenue patch = new FavoriteVenue();
        patch.setLabel("Updated Label");

        FavoriteVenue result = favoriteVenueService.update(5L, patch);

        assertThat(result.getLabel()).isEqualTo("Updated Label");
        verify(favoriteVenueRepository).save(any(FavoriteVenue.class));
        verify(cacheEviction).evictEntityAndFeatures(eq("favorite-venue"), eq(5L), any(String[].class));
    }

    // ══════════════════════════════════════════════════════════════════════
    // delete
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("delete: venue found → deletes and evicts cache")
    void delete_found_deletesAndEvictsCache() {
        when(favoriteVenueRepository.existsById(5L)).thenReturn(true);

        favoriteVenueService.delete(5L);

        verify(favoriteVenueRepository).deleteById(5L);
        verify(cacheEviction).evictEntityAndFeatures(eq("favorite-venue"), eq(5L), any(String[].class));
    }

    @Test
    @DisplayName("delete: venue not found → 404 NOT_FOUND; deleteById never called")
    void delete_notFound_throws404() {
        when(favoriteVenueRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> favoriteVenueService.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FavoriteVenue not found");

        verify(favoriteVenueRepository, never()).deleteById(any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // setDefault
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("setDefault: happy path → clears all defaults, sets venue as default, returns user")
    void setDefault_happyPath_clearsAndSetsDefault() {
        User user = testUser(1L);
        FavoriteVenue venue = venueForUser(5L, 1L);
        when(userService.findById(1L)).thenReturn(user);
        when(favoriteVenueRepository.findById(5L)).thenReturn(Optional.of(venue));

        favoriteVenueService.setDefault(1L, 5L);

        verify(favoriteVenueRepository).clearDefaultsByUserId(1L);
        assertThat(venue.getIsDefault()).isTrue();
        verify(favoriteVenueRepository).save(venue);
        verify(cacheEviction).evictEntityAndFeatures(eq("favorite-venue"), eq(5L), any(String[].class));
        verify(cacheEviction).evictDetail("user", 1L);
    }

    @Test
    @DisplayName("setDefault: venue belongs to different user → 400 BAD_REQUEST")
    void setDefault_venueBelongsToDifferentUser_throws400() {
        User requestUser = testUser(1L);
        FavoriteVenue venue = venueForUser(5L, 2L); // belongs to user 2
        when(userService.findById(1L)).thenReturn(requestUser);
        when(favoriteVenueRepository.findById(5L)).thenReturn(Optional.of(venue));

        assertThatThrownBy(() -> favoriteVenueService.setDefault(1L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to user");

        verify(favoriteVenueRepository, never()).clearDefaultsByUserId(any());
        verify(favoriteVenueRepository, never()).save(any());
    }
}
