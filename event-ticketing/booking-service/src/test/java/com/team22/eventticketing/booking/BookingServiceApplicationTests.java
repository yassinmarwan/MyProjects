package com.team22.eventticketing.booking;

import com.team22.eventticketing.booking.observer.MongoEventLogger;
import com.team22.eventticketing.booking.repository.EventNodeRepository;
import com.team22.eventticketing.booking.repository.UserNodeRecommendationRepository;
import com.team22.eventticketing.booking.repository.UserNodeRepository;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.feign.TicketServiceClient;
import com.team22.eventticketing.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("saga-test")
@Testcontainers
@SuppressWarnings("resource")
class BookingServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("etdb-smoke")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean RedisConnectionFactory redisConnectionFactory;
    @MockitoBean org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @MockitoBean EventServiceClient eventServiceClient;
    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean TicketServiceClient ticketServiceClient;
    @MockitoBean MongoEventLogger mongoEventLogger;
    @MockitoBean Driver neo4jDriver;
    @MockitoBean UserNodeRepository userNodeRepository;
    @MockitoBean EventNodeRepository eventNodeRepository;
    @MockitoBean UserNodeRecommendationRepository userNodeRecommendationRepository;

    @Test
    void contextLoads() {
    }
}
