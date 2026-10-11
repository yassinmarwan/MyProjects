package com.team22.eventticketing.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestCassandraConfig.class)
class TicketServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
