package com.team22.eventticketing.user;

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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserReportControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private BookingServiceClient bookingServiceClient;

    private MockMvc mockMvc;

    private Long userAId;
    private Long userBId;
    private Long userCId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        cleanUp();

        User userA = new User("User A", "test-s1f6-a@test.com", "password", "1000000001", Role.ATTENDEE);
        userAId = userRepository.save(userA).getId();

        User userB = new User("User B", "test-s1f6-b@test.com", "password", "1000000002", Role.ATTENDEE);
        userBId = userRepository.save(userB).getId();

        User userC = new User("User C", "test-s1f6-c@test.com", "password", "1000000003", Role.ATTENDEE);
        userCId = userRepository.save(userC).getId();

        // default: any other user in the DB returns zero so they are filtered out
        when(bookingServiceClient.getUserBookingTotal(anyLong(), anyString(), anyString()))
                .thenReturn(BigDecimal.ZERO);
        when(bookingServiceClient.getTotalBookingCount(anyLong(), anyString()))
                .thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("test-s1f6-"))
                .forEach(u -> userRepository.deleteById(u.getId()));
    }

    // Scenario b: top 2 in March 2026 → User B first, User A second
    @Test
    void topAttendees_returnsTopNOrderedBySpending() throws Exception {
        when(bookingServiceClient.getUserBookingTotal(userAId, "2026-03-01", "2026-03-31"))
                .thenReturn(BigDecimal.valueOf(2000.0));
        when(bookingServiceClient.getTotalBookingCount(userAId, "COMPLETED")).thenReturn(2L);

        when(bookingServiceClient.getUserBookingTotal(userBId, "2026-03-01", "2026-03-31"))
                .thenReturn(BigDecimal.valueOf(5000.0));
        when(bookingServiceClient.getTotalBookingCount(userBId, "COMPLETED")).thenReturn(1L);

        when(bookingServiceClient.getUserBookingTotal(userCId, "2026-03-01", "2026-03-31"))
                .thenReturn(BigDecimal.valueOf(800.0));
        when(bookingServiceClient.getTotalBookingCount(userCId, "COMPLETED")).thenReturn(1L);

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-01")
                        .param("endDate",   "2026-03-31")
                        .param("limit",     "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("User B")))
                .andExpect(jsonPath("$[0].totalSpent", is(5000.0)))
                .andExpect(jsonPath("$[1].name", is("User A")))
                .andExpect(jsonPath("$[1].totalSpent", is(2000.0)));
    }

    // Scenario c: no bookings in range → empty list
    @Test
    void topAttendees_emptyWhenNoBookingsInRange() throws Exception {
        when(bookingServiceClient.getUserBookingTotal(userAId, "2025-01-01", "2025-01-31"))
                .thenReturn(BigDecimal.ZERO);
        when(bookingServiceClient.getTotalBookingCount(userAId, "COMPLETED")).thenReturn(0L);

        when(bookingServiceClient.getUserBookingTotal(userBId, "2025-01-01", "2025-01-31"))
                .thenReturn(BigDecimal.ZERO);
        when(bookingServiceClient.getTotalBookingCount(userBId, "COMPLETED")).thenReturn(0L);

        when(bookingServiceClient.getUserBookingTotal(userCId, "2025-01-01", "2025-01-31"))
                .thenReturn(BigDecimal.ZERO);
        when(bookingServiceClient.getTotalBookingCount(userCId, "COMPLETED")).thenReturn(0L);

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2025-01-01")
                        .param("endDate",   "2025-01-31")
                        .param("limit",     "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // Scenario d: startDate after endDate → 400
    @Test
    void topAttendees_returns400WhenStartAfterEnd() throws Exception {
        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-31")
                        .param("endDate",   "2026-03-01")
                        .param("limit",     "10"))
                .andExpect(status().isBadRequest());
    }
}
