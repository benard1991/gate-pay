package com.gatepay.paymentservice.dto.eventDto;

import com.gatepay.paymentservice.model.enums.PaymentProvider;
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
public class PaymentSuccessEventDTO {
    private String reference;
    private Long userId;
    private Long walletId;
    private BigDecimal amount;
    private String currency;
    private PaymentProvider provider;
    private String gatewayResponse;
    private String authorizationUrl;
    private LocalDateTime timestamp;
}