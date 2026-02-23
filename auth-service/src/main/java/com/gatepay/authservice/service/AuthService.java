package com.gatepay.authservice.service;

import com.gatepay.authservice.dto.LoginOtpResponse;
import com.gatepay.authservice.dto.LoginRequest;
import com.gatepay.authservice.dto.LoginResponse;
import com.gatepay.authservice.dto.VerifyOtpRequest;

public interface AuthService {

    public LoginOtpResponse initiateLogin(LoginRequest request);

    public LoginResponse completeLogin(VerifyOtpRequest request);

    boolean validateToken(String token);
}
