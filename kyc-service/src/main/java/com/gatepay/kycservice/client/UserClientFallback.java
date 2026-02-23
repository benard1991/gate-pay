package com.gatepay.kycservice.client;

import com.gatepay.kycservice.dto.AdminUserResponseDto;
import com.gatepay.kycservice.dto.ApiResponse;
import com.gatepay.kycservice.dto.UserDto;
import com.gatepay.kycservice.exception.UserServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class UserClientFallback implements UserClient {

    @Override
    public ApiResponse<UserDto> getUserProfile(Long userId) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Unable to fetch user profile for userId=" + userId
        );
    }

    @Override
    public ApiResponse<AdminUserResponseDto> updateKycVerified(Long userId, boolean kycVerified) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Unable to fetch user kyc for userId=" + userId
        );
    }

}
