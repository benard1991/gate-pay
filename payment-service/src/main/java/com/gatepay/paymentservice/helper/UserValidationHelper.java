package com.gatepay.paymentservice.helper;

import com.gatepay.paymentservice.client.UserClient;
import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.dto.UserDto;
import com.gatepay.paymentservice.exception.AccountDisabledException;
import com.gatepay.paymentservice.exception.KycException;
import com.gatepay.paymentservice.exception.UserNotFoundException;
import com.gatepay.paymentservice.model.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserValidationHelper {

    private final UserClient userClient;

    /**
     * Validate user exists, is active, and KYC verified
     * @return validated UserDto
     * @throws UserNotFoundException if user doesn't exist
     * @throws AccountDisabledException if account is not active
     * @throws KycException if KYC not verified
     */
    public UserDto validateUserForPayment(Long userId) {
        log.debug("Validating user for payment | userId={}", userId);

        // Fetch user
        ApiResponse<UserDto> userResponse = userClient.getUserProfile(userId);
        if (userResponse == null || userResponse.getData() == null) {
            log.warn("User not found | userId={}", userId);
            throw new UserNotFoundException("User not found | id=" + userId);
        }

        UserDto user = userResponse.getData();

        // Check account status
        if (user.getStatus() != AccountStatus.ACTIVE) {
            log.warn("User account not active | userId={} | status={}", userId, user.getStatus());
            throw new AccountDisabledException("User account is " + user.getStatus());
        }

        // Check KYC
        if (!user.isKycVerified()) {
            log.warn("User KYC not verified | userId={}", userId);
            throw new KycException("User KYC not verified. Kindly contact our support team");
        }

        log.debug("User validation successful | userId={}", userId);
        return user;
    }
}
