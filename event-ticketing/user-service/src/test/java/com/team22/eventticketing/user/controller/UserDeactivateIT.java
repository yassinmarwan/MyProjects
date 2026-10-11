package com.team22.eventticketing.user.controller;

import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserDeactivateIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private BookingServiceClient bookingServiceClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        userRepository.findAll().stream()
                .filter(u -> "deactivate-test@test.com".equals(u.getEmail()))
                .forEach(u -> userRepository.deleteById(u.getId()));

        User user = new User("Deactivate Test", "deactivate-test@test.com", "pass", "0000000000", Role.ATTENDEE);
        userId = userRepository.save(user).getId();
    }

    // Scenario b: deactivate with active booking → 400
    @Test
    void deactivate_returns400WhenActiveBookingExists() throws Exception {
        when(bookingServiceClient.getActiveBookingCount(userId)).thenReturn(1);

        mockMvc.perform(put("/api/users/" + userId + "/deactivate"))
                .andExpect(status().isBadRequest());
    }

    // Scenario d: deactivate with no active bookings → success
    @Test
    void deactivate_succeedsWhenNoActiveBookings() throws Exception {
        when(bookingServiceClient.getActiveBookingCount(userId)).thenReturn(0);

        mockMvc.perform(put("/api/users/" + userId + "/deactivate"))
                .andExpect(status().isOk());

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    // 404 when user not found
    @Test
    void deactivate_returns404WhenUserNotFound() throws Exception {
        mockMvc.perform(put("/api/users/999999/deactivate"))
                .andExpect(status().isNotFound());
    }
}
