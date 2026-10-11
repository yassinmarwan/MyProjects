package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserPreferenceSearchIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        userRepository.deleteAll();

        // User 1 – favoriteCategory: CONCERT
        User u1 = new User("Alice", "alice@test.com", "pass1", "0000000001", Role.ATTENDEE);
        u1.setPreferences(Map.of("favoriteCategory", "CONCERT"));
        userRepository.save(u1);

        // User 2 – favoriteCategory: SPORTS
        User u2 = new User("Bob", "bob@test.com", "pass2", "0000000002", Role.ATTENDEE);
        u2.setPreferences(Map.of("favoriteCategory", "SPORTS"));
        userRepository.save(u2);

        // User 3 – favoriteCategory: CONCERT
        User u3 = new User("Carol", "carol@test.com", "pass3", "0000000003", Role.ATTENDEE);
        u3.setPreferences(Map.of("favoriteCategory", "CONCERT"));
        userRepository.save(u3);
    }

    // ─── (b) Search returns matching users ───────────────────────────────────────

    @Test
    void searchByPreference_returnsTwoUsersForConcert() throws Exception {
        mockMvc.perform(get("/api/users/preferences/search")
                        .param("key", "favoriteCategory")
                        .param("value", "CONCERT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ─── (c) Search returns empty list when no match ──────────────────────────────

    @Test
    void searchByPreference_returnsEmptyListForTheater() throws Exception {
        mockMvc.perform(get("/api/users/preferences/search")
                        .param("key", "favoriteCategory")
                        .param("value", "THEATER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── (d) Blank key returns 400 ────────────────────────────────────────────────

    @Test
    void searchByPreference_blankKeyReturns400() throws Exception {
        mockMvc.perform(get("/api/users/preferences/search")
                        .param("key", "")
                        .param("value", "CONCERT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ─── Additional: blank value returns 400 ──────────────────────────────────────

    @Test
    void searchByPreference_blankValueReturns400() throws Exception {
        mockMvc.perform(get("/api/users/preferences/search")
                        .param("key", "favoriteCategory")
                        .param("value", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
