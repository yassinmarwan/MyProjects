package com.team22.eventticketing.contracts.feign;

import com.team22.eventticketing.contracts.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${feign.user-service.url}")
public interface UserServiceClient {
    @GetMapping("/api/users/{id}")
    UserDTO getUser(@PathVariable("id") Long id);
}