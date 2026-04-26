package com.gatepay.authservice.service;

import com.gatepay.authservice.client.UserClient;
import com.gatepay.authservice.dto.*;
import com.gatepay.authservice.enums.AccountStatus;
import com.gatepay.authservice.exception.AccountDisabledException;
import com.gatepay.authservice.exception.UserNotFoundException;
import com.gatepay.authservice.producer.AuthNotificationProducer;
import com.gatepay.authservice.service.otp.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ForgotPasswordServiceImpl Tests")
class ForgotPasswordServiceImplTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String TEST_EMAIL      = "test@example.com";
    private static final String TEST_FIRST_NAME = "Test";
    private static final String TEST_OTP        = "123456";
    private static final String NEW_PASSWORD    = "newSecurePassword@1";
    private static final int    OTP_TTL_MINUTES = 5;

    // -------------------------------------------------------------------------
    // Mocks & Subject
    // -------------------------------------------------------------------------

    @Mock private UserClient               userClient;
    @Mock private OtpService               otpService;
    @Mock private AuthNotificationProducer notificationProducer;

    @InjectMocks
    private ForgotPasswordServiceImpl service;

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private UserDto activeUser;

    @BeforeEach
    void setUp() {
        activeUser = buildUser(TEST_EMAIL, TEST_FIRST_NAME, AccountStatus.ACTIVE);
    }

    // =========================================================================
    // forgetPasswordOtp()
    // =========================================================================

    @Nested
    @DisplayName("forgetPasswordOtp()")
    class ForgetPasswordOtpTests {

        @Test
        @DisplayName("Should generate OTP, store it, and send notification when user is ACTIVE")
        void shouldSendOtpAndReturnResponse_whenUserIsActive() {
            // Arrange
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(activeUser));
            when(otpService.generateOtp()).thenReturn(TEST_OTP);

            ForgotPasswordRequest request = buildForgotPasswordRequest(TEST_EMAIL);

            // Act
            ForgotPasswordResponse result = service.forgetPasswordOtp(request);

            // Assert — response shape
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);

            // Assert — OTP stored with correct key and TTL
            verify(otpService).storeOtp(TEST_EMAIL, TEST_OTP, OTP_TTL_MINUTES);

            // Assert — notification dispatched with correct payload
            verify(notificationProducer).sendResetOtpEmail(TEST_EMAIL, TEST_OTP, TEST_FIRST_NAME);

            // Assert — no unexpected side effects
            verifyNoMoreInteractions(otpService, notificationProducer);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user data is null in response")
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {
            // Arrange
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(null));

            ForgotPasswordRequest request = buildForgotPasswordRequest(TEST_EMAIL);

            // Act & Assert
            assertThatThrownBy(() -> service.forgetPasswordOtp(request))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(otpService, notificationProducer);
        }

        @ParameterizedTest(name = "Should block reset for status [{0}]")
        @EnumSource(value = AccountStatus.class, names = {"ACTIVE"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should throw AccountDisabledException for all non-ACTIVE statuses")
        void shouldThrowAccountDisabledException_whenAccountIsNotActive(AccountStatus status) {
            // Arrange
            UserDto inactiveUser = buildUser(TEST_EMAIL, TEST_FIRST_NAME, status);
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(inactiveUser));

            ForgotPasswordRequest request = buildForgotPasswordRequest(TEST_EMAIL);

            // Act & Assert
            assertThatThrownBy(() -> service.forgetPasswordOtp(request))
                    .isInstanceOf(AccountDisabledException.class)
                    .hasMessageContaining(status.name());

            verifyNoInteractions(otpService, notificationProducer);
        }
    }

    // =========================================================================
    // resetPassword()
    // =========================================================================

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should verify OTP, call client with correct payload, delete OTP, and notify user")
        void shouldResetPasswordSuccessfully_whenAllConditionsAreMet() {
            // Arrange
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(activeUser));

            // Act
            boolean result = service.resetPassword(TEST_EMAIL, TEST_OTP, NEW_PASSWORD);

            // Assert — return value
            assertThat(result).isTrue();

            // Assert — OTP verified first (before user fetch)
            verify(otpService).verifyOtp(TEST_EMAIL, TEST_OTP);

            // Assert — correct PasswordUpdateRequest sent to downstream client
            ArgumentCaptor<PasswordUpdateRequest> captor =
                    ArgumentCaptor.forClass(PasswordUpdateRequest.class);
            verify(userClient).resetPassword(captor.capture());
            PasswordUpdateRequest sentRequest = captor.getValue();
            assertThat(sentRequest.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(sentRequest.getNewPassword()).isEqualTo(NEW_PASSWORD);

            // Assert — OTP deleted only after successful client call
            verify(otpService).deleteOtp(TEST_EMAIL);

            // Assert — success notification dispatched
            verify(notificationProducer)
                    .sendResetPasswordSuccessEmail(TEST_EMAIL, NEW_PASSWORD, TEST_FIRST_NAME);

            verifyNoMoreInteractions(otpService, notificationProducer);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user data is null in response")
        void shouldThrowUserNotFoundException_whenUserDoesNotExist() {
            // Arrange — OTP passes, but user lookup returns null
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(null));

            // Act & Assert
            assertThatThrownBy(() -> service.resetPassword(TEST_EMAIL, TEST_OTP, NEW_PASSWORD))
                    .isInstanceOf(UserNotFoundException.class);

            // OTP was verified but nothing else should happen
            verify(otpService).verifyOtp(TEST_EMAIL, TEST_OTP);
            verify(otpService, never()).deleteOtp(any());
            verifyNoInteractions(notificationProducer);
        }

        @Test
        @DisplayName("Should return false and preserve OTP when downstream client call fails")
        void shouldReturnFalse_andPreserveOtp_whenDownstreamClientThrows() {
            // Arrange
            when(userClient.getUserByEmail(TEST_EMAIL)).thenReturn(okResponse(activeUser));
            doThrow(new RuntimeException("Downstream failure"))
                    .when(userClient).resetPassword(any(PasswordUpdateRequest.class));

            // Act
            boolean result = service.resetPassword(TEST_EMAIL, TEST_OTP, NEW_PASSWORD);

            // Assert — signals failure to caller
            assertThat(result).isFalse();

            // OTP verified but NOT deleted — reset never completed
            verify(otpService).verifyOtp(TEST_EMAIL, TEST_OTP);
            verify(otpService, never()).deleteOtp(any());

            // No success notification sent
            verifyNoInteractions(notificationProducer);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static UserDto buildUser(String email, String firstName, AccountStatus status) {
        UserDto user = new UserDto();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setStatus(status);
        return user;
    }

    private static ForgotPasswordRequest buildForgotPasswordRequest(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private static <T> ApiResponse<T> okResponse(T data) {
        return new ApiResponse<>(200, "OK", data);
    }
}