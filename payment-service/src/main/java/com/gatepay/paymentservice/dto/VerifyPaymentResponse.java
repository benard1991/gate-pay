package com.gatepay.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyPaymentResponse {

    // Map Paystack's boolean "status" to "success"
    @JsonProperty("status")
    private boolean success;

    private String message;
    private String provider;
    private TransactionData data;

    // Helper method
    public boolean isSuccess() {
        return success;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionData {

        private Long id;

        private String status;  // Transaction status: "success", "failed", "abandoned"

        private String reference;

        private BigDecimal amount;

        private String currency;

        @JsonProperty("gateway_response")
        private String gatewayResponse;

        @JsonProperty("paid_at")
        private LocalDateTime paidAt;

        private String channel;

        @JsonProperty("ip_address")
        private String ipAddress;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;

        private Customer customer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Customer {
        private Long id;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        private String email;

        @JsonProperty("customer_code")
        private String customerCode;

        private String phone;
    }
}