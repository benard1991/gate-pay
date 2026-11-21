package com.gatepay.userservice.controller;

import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.dto.ApiResponse;
import com.gatepay.userservice.dto.PasswordUpdateRequest;
import com.gatepay.userservice.dto.UserDto;
import com.gatepay.userservice.service.AdminService;
import com.gatepay.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Service-to-service endpoints, called directly over the internal network via Feign.
// The gateway only routes /api/v1/** paths, so /internal/** is never reachable from outside.
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final AdminService adminService;

    // Used by auth-service to check a login. The only response that carries the password hash.
    @GetMapping("/credentials")
    public ApiResponse<UserDto> getUserCredentials(@RequestParam("email") String email) {
        return new ApiResponse<>(HttpStatus.OK.value(), "User fetched successfully",
                userService.getUserCredentialsByEmail(email));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserDto> getUser(@PathVariable("userId") Long userId) {
        return new ApiResponse<>(HttpStatus.OK.value(), "User fetched successfully",
                userService.getUserProfile(userId));
    }

    // Called by auth-service only after it has verified the reset OTP
    @PutMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody PasswordUpdateRequest request) {
        boolean success = userService.updatePassword(request.getEmail(), request.getNewPassword());
        if (!success) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to update password", null));
        }
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Password updated successfully", null));
    }

    // Called by kyc-service when a KYC request is approved or rejected
    @PutMapping("/{userId}/kyc-verified")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> updateKycVerified(@PathVariable("userId") Long userId,
                                                                               @RequestParam("kycVerified") boolean kycVerified) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "User KYC verification updated successfully",
                adminService.updateKycVerified(userId, kycVerified)));
    }
}
