package com.gatepay.userservice.service;

import com.gatepay.userservice.config.UserClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "auth-service",
        configuration = UserClientConfig.class,
        fallback = AuthServiceClientFallback.class
)
public interface AuthServiceClient {

    @GetMapping("/auth/validate-token")
    Boolean validateToken(@RequestParam("token") String token);

}
