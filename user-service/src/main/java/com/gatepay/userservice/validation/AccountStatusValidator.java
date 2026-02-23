package com.gatepay.userservice.validation;


import com.gatepay.userservice.enums.AccountStatus;
import com.gatepay.userservice.exception.AccountDisabledException;
import com.gatepay.userservice.model.User;

public final class AccountStatusValidator {

    private AccountStatusValidator() {}

    public static void validateLogin(User user) {
        ensureUserNotNull(user);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException(
                    "Login not allowed. Account status: " + user.getStatus()
            );
        }
    }

    private static void validateActiveAccount(User user) {
        ensureUserNotNull(user);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException(
                    "Account is " + user.getStatus().name().toLowerCase()
            );
        }

    }
    public static void validateProfileAccess(User user) {
        ensureUserNotNull(user);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException(
                    "Profile access denied. Account status: " + user.getStatus()
            );
        }
    }

    public static void validatePasswordReset(User user) {
        ensureUserNotNull(user);

        if (user.getStatus() == AccountStatus.DISABLED ||
                user.getStatus() == AccountStatus.LOCKED) {
            throw new AccountDisabledException(
                    "Password reset not allowed. Account status: " + user.getStatus()
            );
        }
    }

    public static void validateTransaction(User user) {
        ensureUserNotNull(user);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException(
                    "Transactions not allowed. Account status: " + user.getStatus()
            );
        }
    }

    public static void validateWalletCredit(User user) {
        ensureUserNotNull(user);

        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException(
                    "Wallet credit not allowed. Account is disabled"
            );
        }
    }

    private static void ensureUserNotNull(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
    }
}
