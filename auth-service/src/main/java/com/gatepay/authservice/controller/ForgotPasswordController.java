package com.gatepay.authservice.controller;

import com.gatepay.authservice.dto.ApiResponse;
import com.gatepay.authservice.dto.ForgotPasswordRequest;
import com.gatepay.authservice.dto.ForgotPasswordResponse;
import com.gatepay.authservice.dto.PasswordResetRequest;
import com.gatepay.authservice.service.ForgotPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final ForgotPasswordService forgotPasswordService;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgetPassword(
            @RequestBody  @Valid  ForgotPasswordRequest request) {
        ForgotPasswordResponse response = forgotPasswordService.forgetPasswordOtp(request);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OTP sent successfully", response));
    }

    @PutMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        boolean success = forgotPasswordService.resetPassword(request.getEmail(), request.getOtp(),request.getNewPassword());
        if (success) {
            return ResponseEntity.ok(
                    new ApiResponse<>(HttpStatus.OK.value(), "Password reset successfully", null));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to reset password", null));
        }
    }
}
