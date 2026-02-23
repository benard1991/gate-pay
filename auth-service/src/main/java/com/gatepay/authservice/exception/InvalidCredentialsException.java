package com.gatepay.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends AuthServiceException {
    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}