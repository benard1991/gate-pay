package com.gatepay.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetRequest {

    @Email(message = "Invalid email address")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;

    @NotBlank(message = "New password is required")
    private String newPassword;
}
