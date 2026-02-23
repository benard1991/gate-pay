package com.gatepay.paymentservice.client;

import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.dto.UserDto;
import com.gatepay.paymentservice.exception.UserServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class UserClientFallback implements UserClient {

    @Override
    public ApiResponse<UserDto> getUserProfile(Long userId) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Unable to fetch user profile for userId=" + userId
        );
    }

}
