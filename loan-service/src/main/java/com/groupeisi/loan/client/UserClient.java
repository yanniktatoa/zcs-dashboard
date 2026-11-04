package com.groupeisi.loan.client;

import com.groupeisi.loan.dto.UserResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "userService",
        url = "${clients.user.url}",
        fallbackFactory = UserClientFallbackFactory.class
)
public interface UserClient {
    @GetMapping("/users/{id}")
    UserResponseDto getUserById(@PathVariable("id") Long id);
}
