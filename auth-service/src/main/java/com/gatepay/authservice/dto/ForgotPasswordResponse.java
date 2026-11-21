package com.gatepay.authservice.dto;

import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordResponse {
    private String message;
    private String email;
    // only set when gatepay.security.expose-otp-in-response=true (local testing)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private  String otp;
}