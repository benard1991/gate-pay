package com.gatepay.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserNotFoundException extends AuthServiceException{


    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
