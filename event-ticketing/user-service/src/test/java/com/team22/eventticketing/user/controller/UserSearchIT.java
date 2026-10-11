package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [S1-F1] Search Users with Filters
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSearchIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    private List<Long> createdIds;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        User ahmed     = new User("Ahmed",     "s1f1-ahmed@test.com",     "pass", "0100000001", Role.ATTENDEE);
        User sara      = new User("Sara",      "s1f1-sara@test.com",      "pass", "0100000002", Role.ADMIN);
        User ahmedAli  = new User("Ahmed Ali", "s1f1-ahmedali@test.com",  "pass", "0100000003", Role.ATTENDEE);

        createdIds = userRepository.saveAll(List.of(ahmed, sara, ahmedAli))
                .stream().map(User::getId).toList();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllById(createdIds);
    }

    // (b) name=Ahmed → 2 results
    @Test
    void searchByName_returnsTwoAhmedUsers() throws Exception {
        mockMvc.perform(get("/api/users/search").param("name", "Ahmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // (c) role=ADMIN → only Sara
    @Test
    void searchByRole_returnsOnlySara() throws Exception {
        mockMvc.perform(get("/api/users/search").param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Sara"));
    }

    // (d) name=xyz → empty list
    @Test
    void searchByName_noMatch_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/users/search").param("name", "xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // (e) name=ahmed (lowercase) → 2 results (case-insensitive)
    @Test
    void searchByName_caseInsensitive_returnsTwoUsers() throws Exception {
        mockMvc.perform(get("/api/users/search").param("name", "ahmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
