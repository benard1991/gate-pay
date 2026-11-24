package com.gatepay.authservice.dto;

import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@Builder
public class LoginOtpResponse {

    private String message;
    private boolean otpRequired;
    // only set when gatepay.security.expose-otp-in-response=true (local testing)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String otp;
    private String email;


}
