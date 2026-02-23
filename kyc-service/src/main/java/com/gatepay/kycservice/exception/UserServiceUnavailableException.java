package com.gatepay.kycservice.exception;

import org.hibernate.service.spi.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class UserServiceUnavailableException extends KycServiceException{

    // Default message from ErrorCode
    public UserServiceUnavailableException() {
        super(ErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    // Custom message if needed
    public UserServiceUnavailableException(String customMessage) {
        super(ErrorCode.USER_SERVICE_UNAVAILABLE, customMessage);
    }
}