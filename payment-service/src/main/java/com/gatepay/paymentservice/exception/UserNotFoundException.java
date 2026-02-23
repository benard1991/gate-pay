package com.gatepay.paymentservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends PaymentServiceException {


    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }

    public UserNotFoundException(String customMessage) {
        super(ErrorCode.USER_NOT_FOUND, customMessage);
    }
}
