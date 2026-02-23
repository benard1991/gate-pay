package com.gatepay.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Flutterwave API Response DTOs
 * Used for mapping Flutterwave API responses to Java objects
 */
public class FlutterwaveDto {

    /**
     * Response from Flutterwave payment verification endpoint
     * Endpoint: GET /v3/transactions/verify_by_reference?tx_ref={reference}
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyResponse {
        private String status;          // "success" or "error"
        private String message;         // Response message
        private TransactionData data;   // Transaction details
    }

    /**
     * Flutterwave transaction data
     * Contains all transaction information returned by Flutterwave
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionData {
        private Long id;                // Transaction ID

        @JsonProperty("tx_ref")
        private String txRef;           // Your unique transaction reference

        @JsonProperty("flw_ref")
        private String flwRef;          // Flutterwave's reference

        @JsonProperty("device_fingerprint")
        private String deviceFingerprint;

        private BigDecimal amount;      // Transaction amount
        private String currency;        // Currency code (NGN, USD, etc.)

        @JsonProperty("charged_amount")
        private BigDecimal chargedAmount;  // Actual amount charged (includes fees)

        @JsonProperty("app_fee")
        private BigDecimal appFee;      // Application fee

        @JsonProperty("merchant_fee")
        private BigDecimal merchantFee; // Merchant fee

        @JsonProperty("processor_response")
        private String processorResponse;  // Gateway/processor response message

        @JsonProperty("auth_model")
        private String authModel;       // Authentication model used

        private String ip;              // Customer's IP address
        private String narration;       // Transaction narration/description
        private String status;          // Transaction status: "successful", "failed", "pending"

        @JsonProperty("payment_type")
        private String paymentType;     // Payment method: "card", "bank_transfer", "ussd", etc.

        @JsonProperty("created_at")
        private String createdAt;       // Transaction creation timestamp

        @JsonProperty("account_id")
        private Long accountId;         // Flutterwave account ID

        private Customer customer;      // Customer information
        private Card card;              // Card details (if payment via card)

        private Map<String, Object> meta;  // Custom metadata you sent
    }

    /**
     * Customer information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Customer {
        private Long id;                // Customer ID in Flutterwave
        private String name;            // Customer name

        @JsonProperty("phone_number")
        private String phoneNumber;     // Customer phone number

        private String email;           // Customer email

        @JsonProperty("created_at")
        private String createdAt;       // Customer creation timestamp
    }

    /**
     * Card information (only present if payment was made via card)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Card {
        @JsonProperty("first_6digits")
        private String first6digits;    // First 6 digits of card (BIN)

        @JsonProperty("last_4digits")
        private String last4digits;     // Last 4 digits of card

        private String issuer;          // Card issuer (Visa, Mastercard, etc.)
        private String country;         // Card country
        private String type;            // Card type (debit, credit)
        private String token;           // Tokenized card (for recurring payments)
        private String expiry;          // Card expiry (MM/YY format)
    }

    /**
     * Response from Flutterwave payment initiation endpoint
     * Endpoint: POST /v3/payments
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitiateResponse {
        private String status;          // "success" or "error"
        private String message;         // Response message
        private InitiateData data;      // Payment initialization data
    }

    /**
     * Data returned when payment is initiated
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitiateData {
        private String link;            // Payment page URL (redirect customer here)
    }
}