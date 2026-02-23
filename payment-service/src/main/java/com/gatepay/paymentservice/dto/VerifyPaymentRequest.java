package com.gatepay.paymentservice.dto;

import com.gatepay.paymentservice.model.enums.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class VerifyPaymentRequest {

    @NotNull(message = "Reference is required")
    private String reference;

    @NotNull(message = "Provider is required")
    private PaymentProvider provider;
}
