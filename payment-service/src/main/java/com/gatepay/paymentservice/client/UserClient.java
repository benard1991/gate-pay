package com.gatepay.paymentservice.client;


import com.gatepay.paymentservice.config.UserClientConfig;
import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        fallback = UserClientFallback.class,
        configuration = UserClientConfig.class
)
public interface UserClient {
    @GetMapping("/users/{userId}/profile")
    ApiResponse<UserDto> getUserProfile(@PathVariable("userId") Long userId );

}


