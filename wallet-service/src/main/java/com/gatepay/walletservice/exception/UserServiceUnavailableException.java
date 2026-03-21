package com.gatepay.walletservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class UserServiceUnavailableException extends WalletServiceException{

    public UserServiceUnavailableException() {
        super(ErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    public UserServiceUnavailableException(String customMessage) {
        super(ErrorCode.USER_SERVICE_UNAVAILABLE, customMessage);
    }
}