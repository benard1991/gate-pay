package com.gatepay.authservice.service;

import com.gatepay.authservice.client.UserClient;
import com.gatepay.authservice.dto.*;
import com.gatepay.authservice.enums.AccountStatus;
import com.gatepay.authservice.producer.AuthNotificationProducer;
import com.gatepay.authservice.service.otp.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Tests")
class AuthServiceImplTest {

    private static final Long   USER_ID         = 1L;
    private static final String TEST_EMAIL      = "test@example.com";
    private static final String TEST_FIRST_NAME = "Test";
    private static final String TEST_LAST_NAME  = "User";
    private static final String RAW_PASSWORD    = "password123";
    private static final String HASHED_PASSWORD = "hashedPassword";
    private static final String TEST_OTP        = "123456";
    private static final String ACCESS_TOKEN    = "access-token";
    private static final String REFRESH_TOKEN   = "refresh-token";

    @Mock private UserClient               userClient;
    @Mock private PasswordEncoder          passwordEncoder;
    @Mock private JwtService               jwtService;
    @Mock private OtpService               otpService;
    @Mock private AuthNotificationProducer notificationProducer;

    @InjectMocks
    private AuthServiceImpl authService;

    // shared fixtures built once before each test
    private UserDto activeUser;
    private JwtTokens validTokens;

    @BeforeEach
    void setUp() {
        activeUser = buildUser(USER_ID, TEST_EMAIL, TEST_FIRST_NAME, TEST_LAST_NAME,
                HASHED_PASSWORD, AccountStatus.ACTIVE, List.of("USER"));

        validTokens = new JwtTokens(
                ACCESS_TOKEN,  Instant.now().plusSeconds(3600),
                REFRESH_TOKEN, Instant.now().plusSeconds(7200)
        );
    }

    @Nested
    @DisplayName("initiateLogin()")
    class InitiateLoginTests {

        @Test
        @DisplayName("Should generate OTP and return response when credentials are valid")
        void shouldReturnLoginOtpResponse_whenCredentialsAreCorrect() {
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(activeUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(otpService.generateOtp()).thenReturn(TEST_OTP);

            LoginOtpResponse response = authService.initiateLogin(buildLoginRequest(TEST_EMAIL, RAW_PASSWORD));

            // make sure the response carries the right email, OTP, and flag
            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(response.isOtpRequired()).isTrue();
            assertThat(response.getOtp()).isEqualTo(TEST_OTP);
        }
    }

    @Nested
    @DisplayName("completeLogin()")
    class CompleteLoginTests {

        @Test
        @DisplayName("Should return tokens and user info when OTP is valid")
        void shouldReturnLoginResponse_whenOtpIsValid() {
            when(otpService.verifyOtp(TEST_EMAIL, TEST_OTP)).thenReturn(true);
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(activeUser));
            when(jwtService.generateTokens(activeUser)).thenReturn(validTokens);

            LoginResponse response = authService.completeLogin(buildVerifyOtpRequest(TEST_EMAIL, TEST_OTP));

            // both tokens should come back correctly
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);

            // user info should be embedded in the response
            assertThat(response.getUser().getId()).isEqualTo(USER_ID);
            assertThat(response.getUser().getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(response.getUser().getRoles()).contains("USER");
        }
    }

    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return true when the JWT service accepts the token")
        void shouldReturnTrue_whenTokenIsValid() {
            when(jwtService.validateToken(ACCESS_TOKEN)).thenReturn(true);

            // straightforward delegation — just make sure the result passes through
            assertThat(authService.validateToken(ACCESS_TOKEN)).isTrue();
        }
    }

    // helpers — nothing clever, just keeps the test bodies clean

    private static UserDto buildUser(Long id, String email, String firstName, String lastName,
                                     String password, AccountStatus status, List<String> roles) {
        UserDto user = new UserDto();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPassword(password);
        user.setStatus(status);
        user.setRoles(roles);
        return user;
    }

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

    private static <T> ApiResponse<T> okResponse(T data) {
        return new ApiResponse<>(200, "OK", data);
    }
}