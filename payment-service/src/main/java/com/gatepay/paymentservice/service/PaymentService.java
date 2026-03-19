package com.gatepay.paymentservice.service;

import com.gatepay.paymentservice.dto.*;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResponse initializePayment(PaymentRequest request, PaymentProvider provider, String ipAddress);

    public VerifyPaymentResponse verifyPayment(VerifyPaymentRequest verifyPaymentRequest, String performedBy, String ipAddress, String userAgent);

    Page<PaymentTransaction> fetchAllTransactions(int page, int size);

    Page<PaymentTransaction> fetchUserTransactions(String email, int page, int size);

    PaymentTransaction fetchTransactionByReference(String reference);

    public Page<PaymentTransaction> fetchUserTransactionsByUserId(String userId, TransactionFilter filter, int page, int size);
}
