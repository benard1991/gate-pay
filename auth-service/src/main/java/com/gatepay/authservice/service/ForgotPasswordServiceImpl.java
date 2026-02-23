package com.gatepay.authservice.service;

import com.gatepay.authservice.client.UserClient;
import com.gatepay.authservice.dto.*;
import com.gatepay.authservice.enums.AccountStatus;
import com.gatepay.authservice.exception.AccountDisabledException;
import com.gatepay.authservice.exception.UserNotFoundException;
import com.gatepay.authservice.producer.AuthNotificationProducer;
import com.gatepay.authservice.service.otp.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordServiceImpl implements ForgotPasswordService {

    private final UserClient userClient;
    private final OtpService otpService;
    private final AuthNotificationProducer notificationProducer;

    @Override
    public ForgotPasswordResponse forgetPasswordOtp(ForgotPasswordRequest request) {
        ApiResponse<UserDto> response = userClient.getUserByEmail(request.getEmail());

        UserDto user = Optional.ofNullable(response.getData())
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException(
                    "Reset Password allowed. Account status: " + user.getStatus()
            );
        }
        // Generate OTP and store in Redis
        String otp = otpService.generateOtp();
        String otpKey = user.getEmail();
        otpService.storeOtp(otpKey, otp, 5);

        notificationProducer.sendResetOtpEmail(user.getEmail(), otp,user.getFirstName());

        return new ForgotPasswordResponse(
                "OTP sent successfully to email: " + user.getEmail(),
                user.getEmail(),
                otp
        );

    }


    @Override
    public boolean resetPassword(String email, String otp, String newPassword) {

        otpService.verifyOtp(email, otp);

        ApiResponse<UserDto> response = userClient.getUserByEmail(email);
        UserDto user = Optional.ofNullable(response.getData())
                .orElseThrow(UserNotFoundException::new);

        try {
            PasswordUpdateRequest request = new PasswordUpdateRequest();
            request.setEmail(email);
            request.setNewPassword(newPassword);
            userClient.resetPassword(request);
        } catch (Exception ex) {
            log.error("Failed to reset password via UserService for email: {}", email, ex);
            return false;
        }

        otpService.deleteOtp(email);

        notificationProducer.sendResetPasswordSuccessEmail(email, newPassword,user.getFirstName());

        log.info("Password reset successfully for email: {}", email);
        return true;
    }



}

