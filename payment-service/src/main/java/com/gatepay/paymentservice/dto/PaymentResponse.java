package com.gatepay.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private boolean success;
    private String message;
    private String reference;
    private String authorizationUrl;
    private String accessCode;
    private String provider;
}