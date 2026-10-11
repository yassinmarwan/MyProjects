package com.team22.eventticketing.user;

import com.team22.eventticketing.user.entity.Role;
import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.entity.UserStatus;
import com.team22.eventticketing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserServiceApplicationTests {

    @Autowired
    private UserService userService;

    @Test
    void contextLoads() {
    }

    @Test
    void testDeactivateUser() {
        // Given
        User user = new User("Test User", "test@example.com", "password", "1234567890", Role.ATTENDEE);
        User savedUser = userService.create(user);
        
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        
        // When
        User deactivatedUser = userService.deactivateUser(savedUser.getId());
        
        // Then
        assertThat(deactivatedUser.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        
        User fetchedUser = userService.findById(savedUser.getId());
        assertThat(fetchedUser.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        
        // Clean up
        userService.delete(savedUser.getId());
    }

}
