package com.gatepay.walletservice.exception;


public class WalletAlreadyExistsException extends WalletServiceException {
    public WalletAlreadyExistsException() {
        super(ErrorCode.WALLET_ALREADY_EXISTS);
    }
    public WalletAlreadyExistsException(String customMessage) {
        super(ErrorCode.WALLET_ALREADY_EXISTS, customMessage);
    }
}