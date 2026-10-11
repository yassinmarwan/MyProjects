package com.team22.eventticketing.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team22.eventticketing.ticket.repository.TicketScanEventRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestCassandraConfig {

    @Bean
    public TicketScanEventRepository ticketScanEventRepository() {
        return Mockito.mock(TicketScanEventRepository.class);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
