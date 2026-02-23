package com.gatepay.userservice.service;

import com.gatepay.userservice.dto.TokenValidationResponse;
import com.gatepay.userservice.exception.UserServiceUnavailableException;

public class AuthServiceClientFallback implements AuthServiceClient {


    @Override
    public Boolean validateToken(String token) {
        throw new UserServiceUnavailableException(
                "User-Service is currently unavailable. Please try again later."
        );    }


}
