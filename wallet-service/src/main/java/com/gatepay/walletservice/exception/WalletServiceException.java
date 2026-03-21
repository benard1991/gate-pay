package com.gatepay.walletservice.exception;

public class WalletServiceException extends RuntimeException {

    private final ErrorCode errorCode;

    public WalletServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public WalletServiceException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}