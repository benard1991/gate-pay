package com.gatepay.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class UserServiceUnavailableException extends UserServiceException {

    public UserServiceUnavailableException() {
        super(ErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    public UserServiceUnavailableException(String customMessage) {
        super(ErrorCode.USER_SERVICE_UNAVAILABLE, customMessage);
    }
}