package com.gatepay.authservice.client;

import com.gatepay.authservice.config.UserClientConfig;
import com.gatepay.authservice.dto.ApiResponse;
import com.gatepay.authservice.dto.PasswordUpdateRequest;
import com.gatepay.authservice.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "user-service",
        fallback = UserClientFallback.class,
        configuration = UserClientConfig.class
)
public interface UserClient {

    @GetMapping("/users")
    ApiResponse<UserDto> getUserByEmail(@RequestParam("email") String email);

    @PutMapping("/users/reset-password")
    ApiResponse<String> resetPassword(@RequestBody PasswordUpdateRequest request);
}

