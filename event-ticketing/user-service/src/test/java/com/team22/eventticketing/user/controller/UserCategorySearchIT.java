package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [S1-F9] Find Users by Favorite Category with Minimum Bookings
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserCategorySearchIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private BookingServiceClient bookingServiceClient;

    private Long userAId;
    private Long userBId;
    private Long userCId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("s1f9-"))
                .forEach(u -> userRepository.deleteById(u.getId()));

        // User A: favoriteCategory=CONCERT, 5 completed bookings
        User userA = new User("Cat User A", "s1f9-a@test.com", "pass", "0900000001", Role.ATTENDEE);
        userA.setPreferences(Map.of("favoriteCategory", "CONCERT"));
        userAId = userRepository.save(userA).getId();

        // User B: favoriteCategory=CONCERT, 2 completed bookings
        User userB = new User("Cat User B", "s1f9-b@test.com", "pass", "0900000002", Role.ATTENDEE);
        userB.setPreferences(Map.of("favoriteCategory", "CONCERT"));
        userBId = userRepository.save(userB).getId();

        // User C: favoriteCategory=SPORTS, 10 completed bookings
        User userC = new User("Cat User C", "s1f9-c@test.com", "pass", "0900000003", Role.ATTENDEE);
        userC.setPreferences(Map.of("favoriteCategory", "SPORTS"));
        userCId = userRepository.save(userC).getId();

        when(bookingServiceClient.getTotalBookingCount(userAId, "COMPLETED")).thenReturn(5L);
        when(bookingServiceClient.getTotalBookingCount(userBId, "COMPLETED")).thenReturn(2L);
        when(bookingServiceClient.getTotalBookingCount(userCId, "COMPLETED")).thenReturn(10L);
    }

    @AfterEach
    void tearDown() {
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("s1f9-"))
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    // (b) CONCERT + minBookings=3 → only User A
    @Test
    void categorySearch_minBookings3_returnsOnlyUserA() throws Exception {
        mockMvc.perform(get("/api/users/preferences/category")
                        .param("category", "CONCERT")
                        .param("minBookings", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("s1f9-a@test.com"));
    }

    // (c) CONCERT + minBookings=1 → User A and User B
    @Test
    void categorySearch_minBookings1_returnsUserAAndB() throws Exception {
        mockMvc.perform(get("/api/users/preferences/category")
                        .param("category", "CONCERT")
                        .param("minBookings", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // (d) blank category → 400
    @Test
    void categorySearch_blankCategory_returns400() throws Exception {
        mockMvc.perform(get("/api/users/preferences/category")
                        .param("category", "")
                        .param("minBookings", "1"))
                .andExpect(status().isBadRequest());
    }
}
