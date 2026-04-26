package com.gatepay.authservice.service;

import com.gatepay.authservice.client.UserClient;
import com.gatepay.authservice.dto.*;
import com.gatepay.authservice.enums.AccountStatus;
import com.gatepay.authservice.exception.AccountDisabledException;
import com.gatepay.authservice.exception.InvalidCredentialsException;
import com.gatepay.authservice.exception.UserNotFoundException;
import com.gatepay.authservice.producer.AuthNotificationProducer;
import com.gatepay.authservice.service.otp.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserClient userClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private  final AuthNotificationProducer notificationProducer;

    @Override
    public LoginOtpResponse initiateLogin(LoginRequest request) {
        ApiResponse<UserDto> response = userClient.getUserByEmail(request.getEmail());

        UserDto userDto = Optional.ofNullable(response.getData())
                .orElseThrow(UserNotFoundException::new);

        if (userDto.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException(
                    "Login not allowed. Account status: " + userDto.getStatus()
            );
        }

        if (!passwordEncoder.matches(request.getPassword(), userDto.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // Generate OTP and store in Redis
        String otp = otpService.generateOtp();
        String otpKey = userDto.getEmail();
        otpService.storeOtp(otpKey, otp, 5);


        // Publish OTP to Notification Service via RabbitMQ
        notificationProducer.sendLoginOtpEmail(userDto.getEmail(),otp,userDto.getFirstName());
        return LoginOtpResponse.builder()
                .message("OTP has been sent to your email. Please verify to complete login.")
                .otpRequired(true)
                .otp(otp)
                .email(userDto.getEmail())
                .build();
    }

    @Override
    public LoginResponse completeLogin(VerifyOtpRequest request) {
        otpService.verifyOtp(request.getEmail(),request.getOtp());

        ApiResponse<UserDto> response = userClient.getUserByEmail(request.getEmail());
        UserDto userDto = Optional.ofNullable(response.getData())
                .orElseThrow(UserNotFoundException::new);

        JwtTokens tokens = jwtService.generateTokens(userDto);

        UserInfo userInfo = new UserInfo(
                userDto.getId(),
                userDto.getEmail(),
                userDto.getFirstName(),
                userDto.getLastName(),
                userDto.getStatus(),
                userDto.getRoles()

        );

        // Publish login mail to Notification Service via RabbitMQ
        notificationProducer.sendLoginEmail(request.getEmail(),userDto.getFirstName());

        return new LoginResponse(
                tokens.getAccessToken(),
                tokens.getAccessTokenExpiresAt(),
                tokens.getRefreshToken(),
                tokens.getRefreshTokenExpiresAt(),
                userInfo
        );
    }

    @Override
    public boolean validateToken(String token) {
        return jwtService.validateToken(token);
    }
}
