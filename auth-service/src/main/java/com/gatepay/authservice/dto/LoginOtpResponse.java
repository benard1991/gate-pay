package com.gatepay.authservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginOtpResponse {

    private String message;
    private boolean otpRequired;
    private String otp;
    private String email;


}
