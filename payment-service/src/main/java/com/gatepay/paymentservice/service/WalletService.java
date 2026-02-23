package com.gatepay.paymentservice.service;

import com.gatepay.paymentservice.model.Payment;

import java.math.BigDecimal;

public interface WalletService {

    Payment deposit(Long userId, BigDecimal amount, String provider, String idempotencyKey) ;

}
