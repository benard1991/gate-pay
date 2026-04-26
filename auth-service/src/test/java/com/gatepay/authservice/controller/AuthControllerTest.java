package com.gatepay.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.authservice.config.SecurityConfig;
import com.gatepay.authservice.dto.*;
import com.gatepay.authservice.enums.AccountStatus;
import com.gatepay.authservice.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    private static final String TEST_EMAIL    = "test@example.com";
    private static final String RAW_PASSWORD  = "password123";
    private static final String TEST_OTP      = "123456";
    private static final String ACCESS_TOKEN  = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Nested
    @DisplayName("POST /auth/initiateLogin")
    class InitiateLoginTests {

        @Test
        @DisplayName("Should return 200 and OTP response when credentials are valid")
        void shouldReturn200_whenCredentialsAreValid() throws Exception {
            LoginRequest request = buildLoginRequest(TEST_EMAIL, RAW_PASSWORD);

            LoginOtpResponse otpResponse = LoginOtpResponse.builder()
                    .email(TEST_EMAIL)
                    .otpRequired(true)
                    .otp(TEST_OTP)
                    .build();

            when(authService.initiateLogin(request)).thenReturn(otpResponse);

            mockMvc.perform(post("/auth/initiateLogin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                    .andExpect(jsonPath("$.otpRequired").value(true))
                    .andExpect(jsonPath("$.otp").value(TEST_OTP));
        }

        @Test
        @DisplayName("Should return 400 when email is missing")
        void shouldReturn400_whenEmailIsMissing() throws Exception {
            // @Valid should reject a request with no email before it hits the service
            LoginRequest request = buildLoginRequest(null, RAW_PASSWORD);

            mockMvc.perform(post("/auth/initiateLogin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is missing")
        void shouldReturn400_whenPasswordIsMissing() throws Exception {
            LoginRequest request = buildLoginRequest(TEST_EMAIL, null);

            mockMvc.perform(post("/auth/initiateLogin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /auth/verify-otp")
    class CompleteLoginTests {

        @Test
        @DisplayName("Should return 200 with tokens and user info when OTP is valid")
        void shouldReturn200_whenOtpIsValid() throws Exception {
            VerifyOtpRequest request = buildVerifyOtpRequest(TEST_EMAIL, TEST_OTP);

            UserInfo userInfo = new UserInfo(1L, TEST_EMAIL, "Test", "User", AccountStatus.ACTIVE, List.of("USER"));

            LoginResponse loginResponse = new LoginResponse(
                    ACCESS_TOKEN,  Instant.now().plusSeconds(3600),
                    REFRESH_TOKEN, Instant.now().plusSeconds(7200),
                    userInfo
            );

            when(authService.completeLogin(request)).thenReturn(loginResponse);

            mockMvc.perform(post("/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                    .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN))
                    .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));
        }

        @Test
        @DisplayName("Should return 400 when OTP is missing")
        void shouldReturn400_whenOtpIsMissing() throws Exception {
            VerifyOtpRequest request = buildVerifyOtpRequest(TEST_EMAIL, null);

            mockMvc.perform(post("/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when email is missing")
        void shouldReturn400_whenEmailIsMissing() throws Exception {
            VerifyOtpRequest request = buildVerifyOtpRequest(null, TEST_OTP);

            mockMvc.perform(post("/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /auth/validate-token")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return 200 with true when token is valid")
        void shouldReturn200WithTrue_whenTokenIsValid() throws Exception {
            when(authService.validateToken(ACCESS_TOKEN)).thenReturn(true);

            mockMvc.perform(get("/auth/validate-token")
                            .param("token", ACCESS_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Should return 200 with false when token is invalid")
        void shouldReturn200WithFalse_whenTokenIsInvalid() throws Exception {
            when(authService.validateToken("bad-token")).thenReturn(false);

            mockMvc.perform(get("/auth/validate-token")
                            .param("token", "bad-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Should return 400 when token param is missing")
        void shouldReturn400_whenTokenParamIsMissing() throws Exception {
            // token is a required @RequestParam so Spring should reject this immediately
            mockMvc.perform(get("/auth/validate-token"))
                    .andExpect(status().isBadRequest());
        }
    }

    // helpers

    private static LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static VerifyOtpRequest buildVerifyOtpRequest(String email, String otp) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(email);
        request.setOtp(otp);
        return request;
    }
}