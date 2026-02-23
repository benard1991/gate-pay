package com.gatepay.authservice.service;

import com.gatepay.authservice.client.UserClient;
import com.gatepay.authservice.dto.*;

import com.gatepay.authservice.enums.AccountStatus;
import com.gatepay.authservice.producer.AuthNotificationProducer;
import com.gatepay.authservice.service.otp.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserClient userClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private OtpService otpService;

    @Mock
    private AuthNotificationProducer notificationProducer;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void initiateLogin_shouldReturnLoginOtpResponse_whenCredentialsAreCorrect() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        UserDto userDto = new UserDto();
        userDto.setId(1L);
        userDto.setEmail("test@example.com");
        userDto.setPassword("hashedPassword");
        userDto.setRoles(List.of("USER"));
        userDto.setStatus(AccountStatus.valueOf("ACTIVE"));

        // Correct ApiResponse constructor usage
        ApiResponse<UserDto> userResponse = new ApiResponse<>(200, "OK", userDto);

        when(userClient.getUserByEmail("test@example.com")).thenReturn(userResponse);
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(otpService.generateOtp()).thenReturn("123456");

        LoginOtpResponse response = authService.initiateLogin(request);

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        assertTrue(response.isOtpRequired());
        assertEquals("123456", response.getOtp());
    }

    @Test
    void completeLogin_shouldReturnLoginResponse_whenOtpIsValid() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        UserDto userDto = new UserDto();
        userDto.setId(1L);
        userDto.setEmail("test@example.com");
        userDto.setFirstName("Test");
        userDto.setLastName("User");
        userDto.setPassword("hashedPassword");
        userDto.setRoles(List.of("USER"));
        userDto.setStatus(AccountStatus.valueOf("ACTIVE"));

        ApiResponse<UserDto> userResponse = new ApiResponse<>(200, "OK", userDto);

        JwtTokens tokens = new JwtTokens(
                "access-token",
                Instant.now().plusSeconds(3600),
                "refresh-token",
                Instant.now().plusSeconds(7200)
        );

        when(otpService.verifyOtp("test@example.com", "123456")).thenReturn(true);
        when(userClient.getUserByEmail("test@example.com")).thenReturn(userResponse);
        when(jwtService.generateTokens(userDto)).thenReturn(tokens);

        LoginResponse response = authService.completeLogin(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(1L, response.getUser().getId());
        assertEquals("test@example.com", response.getUser().getEmail());
        assertTrue(response.getUser().getRoles().contains("USER"));
    }

    @Test
    void validateToken_shouldReturnTrue_whenJwtServiceValidates() {
        String token = "jwt-token";
        when(jwtService.validateToken(token)).thenReturn(true);

        boolean isValid = authService.validateToken(token);

        assertTrue(isValid);
    }
}
