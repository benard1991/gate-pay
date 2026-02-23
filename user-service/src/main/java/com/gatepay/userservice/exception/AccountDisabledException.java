package com.gatepay.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountDisabledException extends UserServiceException {
    public AccountDisabledException() {
        super(ErrorCode.ACCOUNT_DISABLED);
    }

    public AccountDisabledException(String customMessage) {
        super(ErrorCode.ACCOUNT_DISABLED, customMessage);
    }
}