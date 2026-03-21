package com.gatepay.walletservice.dto;


import com.gatepay.walletservice.enums.TransactionSource;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebitWalletRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotNull(message = "Transaction source is required")
    private TransactionSource source;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 64)
    private String idempotencyKey;

    @Size(max = 255)
    private String description;

    private String metadata;
}
