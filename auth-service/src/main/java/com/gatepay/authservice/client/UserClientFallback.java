package com.gatepay.authservice.client;

import com.gatepay.authservice.dto.ApiResponse;
import com.gatepay.authservice.dto.PasswordUpdateRequest;
import com.gatepay.authservice.dto.UserDto;
import com.gatepay.authservice.exception.UserServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class UserClientFallback implements UserClient {

    @Override
    public ApiResponse<UserDto> getUserByEmail(String email) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Please try again later."
        );
    }

    @Override
    public ApiResponse<String> resetPassword(PasswordUpdateRequest request) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Unable to reset password."
        );
    }
}
