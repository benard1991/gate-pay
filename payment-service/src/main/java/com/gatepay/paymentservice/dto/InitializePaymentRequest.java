package com.gatepay.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class InitializePaymentRequest {

    private String email;

    private BigDecimal amount; // Amount in kobo (for NGN) or smallest currency unit

    private String reference;

    @JsonProperty("callback_url")
    private String callbackUrl;

    private String currency; // NGN, USD, GHS, ZAR, etc.

    private Metadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        @JsonProperty("customer_name")
        private String customerName;

        @JsonProperty("custom_fields")
        private Map<String, Object> customFields;
    }
}