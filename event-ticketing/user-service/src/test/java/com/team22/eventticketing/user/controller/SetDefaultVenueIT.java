package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.user.entity.FavoriteVenue;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.repository.FavoriteVenueRepository;
import com.team22.eventticketing.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [S1-F7] Set Default Favorite Venue
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SetDefaultVenueIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FavoriteVenueRepository favoriteVenueRepository;

    private Long userId;
    private Long otherUserId;
    private Long venue1Id;
    private Long venue2Id;
    private Long venue3Id;
    private Long otherVenueId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        User user = new User("Venue User", "s1f7-venue@test.com", "pass", "0700000001", Role.ATTENDEE);
        userId = userRepository.save(user).getId();
        User saved = userRepository.findById(userId).orElseThrow();

        FavoriteVenue v1 = new FavoriteVenue("Venue 1", "Opera House", "Cairo", saved);
        FavoriteVenue v2 = new FavoriteVenue("Venue 2", "Stadium", "Giza", saved);
        v2.setIsDefault(true);
        FavoriteVenue v3 = new FavoriteVenue("Venue 3", "Arena", "Alex", saved);

        venue1Id = favoriteVenueRepository.save(v1).getId();
        venue2Id = favoriteVenueRepository.save(v2).getId();
        venue3Id = favoriteVenueRepository.save(v3).getId();

        User other = new User("Other User", "s1f7-other@test.com", "pass", "0700000002", Role.ATTENDEE);
        otherUserId = userRepository.save(other).getId();
        User savedOther = userRepository.findById(otherUserId).orElseThrow();

        FavoriteVenue ov = new FavoriteVenue("Other Venue", "Other Place", "Somewhere", savedOther);
        otherVenueId = favoriteVenueRepository.save(ov).getId();
    }

    @AfterEach
    void tearDown() {
        favoriteVenueRepository.deleteAllById(List.of(venue1Id, venue2Id, venue3Id, otherVenueId));
        userRepository.deleteAllById(List.of(userId, otherUserId));
    }

    // (b) set venue3 as default — venue3 becomes default, venue2 loses it
    @Test
    void setDefault_changesDefaultVenue() throws Exception {
        mockMvc.perform(put("/api/users/" + userId + "/venues/" + venue3Id + "/default"))
                .andExpect(status().isOk());

        FavoriteVenue updatedV3 = favoriteVenueRepository.findById(venue3Id).orElseThrow();
        FavoriteVenue updatedV2 = favoriteVenueRepository.findById(venue2Id).orElseThrow();

        assert updatedV3.getIsDefault() : "venue3 should be default";
        assert !updatedV2.getIsDefault() : "venue2 should no longer be default";
    }

    // (c) non-existent user → 404
    @Test
    void setDefault_nonExistentUser_returns404() throws Exception {
        mockMvc.perform(put("/api/users/999999/venues/" + venue1Id + "/default"))
                .andExpect(status().isNotFound());
    }

    // (d) venue belongs to a different user → 400
    @Test
    void setDefault_venueBelongsToDifferentUser_returns400() throws Exception {
        mockMvc.perform(put("/api/users/" + userId + "/venues/" + otherVenueId + "/default"))
                .andExpect(status().isBadRequest());
    }
}
