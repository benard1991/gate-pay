package com.gatepay.walletservice.client;

import com.gatepay.walletservice.config.UserClientConfig;
import com.gatepay.walletservice.dto.AdminUserResponseDto;
import com.gatepay.walletservice.dto.ApiResponse;
import com.gatepay.walletservice.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "user-service",
        fallback = UserServiceClientFallback.class,
        configuration = UserClientConfig.class
)
public interface UserServiceClient {

    @GetMapping("/internal/users/{userId}")
    ApiResponse<UserDto> getUserProfile(@PathVariable("userId") Long userId);

    @PutMapping("/internal/users/{userId}/kyc-verified")
    ApiResponse<AdminUserResponseDto> updateKycVerified(
            @PathVariable("userId") Long userId,
            @RequestParam("kycVerified") boolean kycVerified
    );
}