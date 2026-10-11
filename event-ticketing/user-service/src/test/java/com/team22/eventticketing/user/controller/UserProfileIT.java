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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [S1-F8] Get User Profile with Favorite Venues
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserProfileIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FavoriteVenueRepository favoriteVenueRepository;

    private Long userId;
    private Long emptyUserId;
    private List<Long> venueIds;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        User user = new User("Profile User", "s1f8-profile@test.com", "pass", "0800000001", Role.ATTENDEE);
        user.setPreferences(Map.of("language", "en"));
        userId = userRepository.save(user).getId();
        User saved = userRepository.findById(userId).orElseThrow();

        FavoriteVenue v1 = new FavoriteVenue("Fav 1", "Opera", "Cairo", saved);
        FavoriteVenue v2 = new FavoriteVenue("Fav 2", "Stadium", "Giza", saved);
        FavoriteVenue v3 = new FavoriteVenue("Fav 3", "Arena", "Alex", saved);
        v3.setIsDefault(true);

        venueIds = favoriteVenueRepository.saveAll(List.of(v1, v2, v3))
                .stream().map(FavoriteVenue::getId).toList();

        User emptyUser = new User("Empty Profile", "s1f8-empty@test.com", "pass", "0800000002", Role.ATTENDEE);
        emptyUserId = userRepository.save(emptyUser).getId();
    }

    @AfterEach
    void tearDown() {
        favoriteVenueRepository.deleteAllById(venueIds);
        userRepository.deleteAllById(List.of(userId, emptyUserId));
    }

    // (b) full profile with 3 venues
    @Test
    void getProfile_returnsFullDTO() throws Exception {
        mockMvc.perform(get("/api/users/" + userId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value("Profile User"))
                .andExpect(jsonPath("$.email").value("s1f8-profile@test.com"))
                .andExpect(jsonPath("$.preferences.language").value("en"))
                .andExpect(jsonPath("$.favoriteVenues.length()").value(3))
                .andExpect(jsonPath("$.totalFavoriteVenues").value(3));
    }

    // (c) user with no venues → empty list, count 0
    @Test
    void getProfile_noVenues_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/users/" + emptyUserId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteVenues.length()").value(0))
                .andExpect(jsonPath("$.totalFavoriteVenues").value(0));
    }

    // (d) non-existent user → 404
    @Test
    void getProfile_nonExistentUser_returns404() throws Exception {
        mockMvc.perform(get("/api/users/999999/profile"))
                .andExpect(status().isNotFound());
    }
}
