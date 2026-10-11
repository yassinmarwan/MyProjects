package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [S1-F2] Update User Preferences (JSONB)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserPreferencesIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        User user = new User("Pref User", "s1f2-pref@test.com", "pass", "0200000001", Role.ATTENDEE);
        user.setPreferences(Map.of("language", "en", "favoriteCategory", "CONCERT"));
        userId = userRepository.save(user).getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
    }

    // (b)+(c) merge: language kept, favoriteCategory updated, ticketTier added
    @Test
    void updatePreferences_mergesCorrectly() throws Exception {
        String body = """
                {"favoriteCategory":"SPORTS","ticketTier":"VIP"}
                """;

        mockMvc.perform(put("/api/users/" + userId + "/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.language").value("en"))
                .andExpect(jsonPath("$.preferences.favoriteCategory").value("SPORTS"))
                .andExpect(jsonPath("$.preferences.ticketTier").value("VIP"));
    }

    // (d) non-existent user → 404
    @Test
    void updatePreferences_nonExistentUser_returns404() throws Exception {
        mockMvc.perform(put("/api/users/999999/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
