package com.gatepay.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentResponseInternal {
    private boolean success;
    private String status;
    private String message;
    private String provider;
    private BigDecimal amount;
    private String reference;
    private String currency;
    private String gatewayResponse;
    private LocalDateTime paidAt;
    private String transactionId;
}
