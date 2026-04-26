package com.gatepay.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.authservice.config.SecurityConfig;
import com.gatepay.authservice.dto.*;
import com.gatepay.authservice.service.ForgotPasswordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ForgotPasswordController.class)
@Import(SecurityConfig.class)
@DisplayName("ForgotPasswordController Tests")
class ForgotPasswordControllerTest {

    private static final String TEST_EMAIL    = "test@example.com";
    private static final String TEST_OTP      = "123456";
    private static final String NEW_PASSWORD  = "newSecurePassword@1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ForgotPasswordService forgotPasswordService;

    @Nested
    @DisplayName("POST /auth/forgot-password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Should return 200 and OTP response when email is valid")
        void shouldReturn200_whenEmailIsValid() throws Exception {
            ForgotPasswordRequest request = buildForgotPasswordRequest(TEST_EMAIL);

            ForgotPasswordResponse otpResponse = new ForgotPasswordResponse(
                    "OTP sent successfully to email: " + TEST_EMAIL,
                    TEST_EMAIL,
                    TEST_OTP
            );

            when(forgotPasswordService.forgetPasswordOtp(request)).thenReturn(otpResponse);

            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("OTP sent successfully"))
                    .andExpect(jsonPath("$.data.email").value(TEST_EMAIL));
        }

        @Test
        @DisplayName("Should return 400 when email is missing")
        void shouldReturn400_whenEmailIsMissing() throws Exception {
            // @Valid kicks in before the service is even called
            ForgotPasswordRequest request = buildForgotPasswordRequest(null);

            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when email format is invalid")
        void shouldReturn400_whenEmailFormatIsInvalid() throws Exception {
            ForgotPasswordRequest request = buildForgotPasswordRequest("not-an-email");

            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /auth/reset-password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should return 200 when password reset succeeds")
        void shouldReturn200_whenResetSucceeds() throws Exception {
            PasswordResetRequest request = buildResetRequest(TEST_EMAIL, TEST_OTP, NEW_PASSWORD);

            when(forgotPasswordService.resetPassword(TEST_EMAIL, TEST_OTP, NEW_PASSWORD))
                    .thenReturn(true);

            mockMvc.perform(put("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("Password reset successfully"));
        }

        @Test
        @DisplayName("Should return 500 when downstream password reset fails")
        void shouldReturn500_whenResetFails() throws Exception {
            PasswordResetRequest request = buildResetRequest(TEST_EMAIL, TEST_OTP, NEW_PASSWORD);

            // service returns false when the downstream client call fails
            when(forgotPasswordService.resetPassword(TEST_EMAIL, TEST_OTP, NEW_PASSWORD))
                    .thenReturn(false);

            mockMvc.perform(put("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Failed to reset password"));
        }

        @Test
        @DisplayName("Should return 400 when email is missing")
        void shouldReturn400_whenEmailIsMissing() throws Exception {
            PasswordResetRequest request = buildResetRequest(null, TEST_OTP, NEW_PASSWORD);

            mockMvc.perform(put("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when OTP is missing")
        void shouldReturn400_whenOtpIsMissing() throws Exception {
            PasswordResetRequest request = buildResetRequest(TEST_EMAIL, null, NEW_PASSWORD);

            mockMvc.perform(put("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when new password is missing")
        void shouldReturn400_whenNewPasswordIsMissing() throws Exception {
            PasswordResetRequest request = buildResetRequest(TEST_EMAIL, TEST_OTP, null);

            mockMvc.perform(put("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // helpers

    private static ForgotPasswordRequest buildForgotPasswordRequest(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private static PasswordResetRequest buildResetRequest(String email, String otp, String newPassword) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(email);
        request.setOtp(otp);
        request.setNewPassword(newPassword);
        return request;
    }
}