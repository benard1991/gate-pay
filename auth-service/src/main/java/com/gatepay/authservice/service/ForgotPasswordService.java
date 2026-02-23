package com.gatepay.authservice.service;


import com.gatepay.authservice.dto.ForgotPasswordRequest;
import com.gatepay.authservice.dto.ForgotPasswordResponse;

public interface ForgotPasswordService {

    ForgotPasswordResponse forgetPasswordOtp(ForgotPasswordRequest request);

    public boolean resetPassword(String email, String otp, String newPassword) ;
}
