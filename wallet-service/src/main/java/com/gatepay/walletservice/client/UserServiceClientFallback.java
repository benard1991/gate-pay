package com.gatepay.walletservice.client;

import com.gatepay.walletservice.dto.AdminUserResponseDto;
import com.gatepay.walletservice.dto.ApiResponse;
import com.gatepay.walletservice.dto.UserDto;
import com.gatepay.walletservice.exception.UserServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public ApiResponse<UserDto> getUserProfile(Long userId) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Unable to fetch user profile for userId=" + userId
        );
    }

    @Override
    public ApiResponse<AdminUserResponseDto> updateKycVerified(Long userId, boolean kycVerified) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Unable to update KYC for userId=" + userId
        );
    }
}