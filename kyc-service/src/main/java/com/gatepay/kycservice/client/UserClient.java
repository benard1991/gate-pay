package com.gatepay.kycservice.client;

import com.gatepay.kycservice.config.UserClientConfig;
import com.gatepay.kycservice.dto.AdminUserResponseDto;
import com.gatepay.kycservice.dto.ApiResponse;
import com.gatepay.kycservice.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "user-service",
        fallback = UserClientFallback.class,
        configuration = UserClientConfig.class
)
public interface UserClient {
    @GetMapping("/users/{userId}/profile")
    ApiResponse<UserDto> getUserProfile(@PathVariable("userId") Long userId );

    @PutMapping("/users/admin/{userId}/updateKycVerified")
    ApiResponse<AdminUserResponseDto> updateKycVerified(
            @PathVariable("userId") Long userId,
            @RequestParam("kycVerified") boolean kycVerified
    );

}


