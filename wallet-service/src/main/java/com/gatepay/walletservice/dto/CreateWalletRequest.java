package com.gatepay.walletservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWalletRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private String currency = "NGN";
}