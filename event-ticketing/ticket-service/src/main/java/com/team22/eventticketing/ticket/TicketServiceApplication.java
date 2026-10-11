package com.team22.eventticketing.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.team22.eventticketing.ticket",
        "com.team22.eventticketing.contracts.dto"
})
@EnableFeignClients(basePackages = {
        "com.team22.eventticketing.contracts.feign"
})
@EnableCaching
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }

}
