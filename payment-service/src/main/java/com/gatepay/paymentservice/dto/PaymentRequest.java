package com.gatepay.paymentservice.dto;

import com.gatepay.paymentservice.model.PaymentTransaction;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Wallet ID is required")
    private Long walletId;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    private String email;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String reference;

    @NotBlank(message = "Callback URL is required")
    @Pattern(
            regexp = "^(https?://).+",
            message = "Callback URL must be a valid URL starting with http:// or https://"
    )
    private String callbackUrl;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    private Map<String, Object> metadata;


}