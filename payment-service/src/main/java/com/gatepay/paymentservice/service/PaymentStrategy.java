package com.gatepay.paymentservice.service;

import com.gatepay.paymentservice.dto.PaymentRequest;
import com.gatepay.paymentservice.dto.PaymentResponse;
import com.gatepay.paymentservice.dto.VerifyPaymentResponse;

public interface PaymentStrategy {

    /**
     * Initialize a payment transaction
     */
    PaymentResponse initiatePayment(PaymentRequest request);

    /**
     * Verify a payment transaction
     */
    VerifyPaymentResponse verifyPayment(String reference);

    /**
     * Get the payment provider name
     */
    String getProviderName();

    /**
     * Check if provider supports the given currency
     */
    boolean supportsCurrency(String currency);
}