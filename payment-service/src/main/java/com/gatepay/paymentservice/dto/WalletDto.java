package com.gatepay.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// What payment-service needs to know about a wallet before taking money
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDto {
    private Long id;
    private Long userId;
    private String currency;
    private String status;
}
