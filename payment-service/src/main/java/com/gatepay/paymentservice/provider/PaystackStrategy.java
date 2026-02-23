package com.gatepay.paymentservice.provider;

import com.gatepay.paymentservice.dto.InitializePaymentRequest;
import com.gatepay.paymentservice.dto.PaymentRequest;
import com.gatepay.paymentservice.dto.PaymentResponse;
import com.gatepay.paymentservice.dto.VerifyPaymentResponse;
import com.gatepay.paymentservice.dto.paystack.InitializePaymentResponse;
import com.gatepay.paymentservice.service.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaystackStrategy implements PaymentStrategy {

    @Value("${paystack.secret-key}")
    private String secretKey;

    @Value("${paystack.base-url}")
    private String baseUrl;

    @Value("${paystack.default-currency:NGN}")
    private String defaultCurrency;

    private static final List<String> SUPPORTED_CURRENCIES = List.of("NGN", "USD", "GHS", "ZAR", "KES");

    @Override
    public PaymentResponse initiatePayment(PaymentRequest request) {
        log.info("Initiating Paystack payment for reference: {}", request.getReference());

        try {
            // Normalize and validate currency
            String currency = normalizeCurrency(request.getCurrency());
            log.info("Request currency: {} | Normalized to: {}", request.getCurrency(), currency);

            // Convert amount to kobo (smallest unit)
            BigDecimal amountInKobo = request.getAmount().multiply(BigDecimal.valueOf(100));

            InitializePaymentRequest paystackRequest = InitializePaymentRequest.builder()
                    .email(request.getEmail())
                    .amount(amountInKobo)
                    .reference(request.getReference())
                    .callbackUrl(request.getCallbackUrl())
                    .currency(currency)
                    .metadata(InitializePaymentRequest.Metadata.builder()
                            .customerName(request.getCustomerName())
                            .customFields(request.getMetadata())
                            .build())
                    .build();

            log.info("Paystack request: email={}, amount={} kobo, currency={}, ref={}",
                    request.getEmail(), amountInKobo, currency, request.getReference());

            InitializePaymentResponse response = createWebClient()
                    .post()
                    .uri("/transaction/initialize")
                    .bodyValue(paystackRequest)
                    .retrieve()
                    .bodyToMono(InitializePaymentResponse.class)
                    .block();

            if (response != null && response.isStatus()) {
                log.info("Paystack payment initialized successfully: {}", request.getReference());
                return PaymentResponse.builder()
                        .success(true)
                        .message(response.getMessage())
                        .reference(request.getReference())
                        .authorizationUrl(response.getData().getAuthorizationUrl())
                        .accessCode(response.getData().getAccessCode())
                        .provider("PAYSTACK")
                        .build();
            }

            log.warn("Paystack initialization failed: {}", response != null ? response.getMessage() : "null response");
            return PaymentResponse.builder()
                    .success(false)
                    .message(response != null ? response.getMessage() : "Failed to initialize payment")
                    .provider("PAYSTACK")
                    .build();

        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("Paystack API error [{}]: {}", e.getStatusCode(), errorBody);

            // Extract user-friendly message
            String message = extractErrorMessage(errorBody);

            return PaymentResponse.builder()
                    .success(false)
                    .message(message)
                    .provider("PAYSTACK")
                    .build();
        } catch (Exception e) {
            log.error("Error initiating Paystack payment: {}", e.getMessage(), e);
            return PaymentResponse.builder()
                    .success(false)
                    .message("Payment initialization failed: " + e.getMessage())
                    .provider("PAYSTACK")
                    .build();
        }
    }

    @Override
    public VerifyPaymentResponse verifyPayment(String reference) {
        log.info("Verifying Paystack payment for reference: {}", reference);

        try {
            VerifyPaymentResponse response = createWebClient()
                    .get()
                    .uri("/transaction/verify/{reference}", reference)
                    .retrieve()
                    .bodyToMono(VerifyPaymentResponse.class)
                    .block();

            // Add logging to debug the response
            log.info("Paystack response - success: {}, message: {}, data: {}",
                    response != null ? response.isSuccess() : null,
                    response != null ? response.getMessage() : null,
                    response != null ? response.getData() : null);

            if (response != null && response.isSuccess() && response.getData() != null) {
                VerifyPaymentResponse.TransactionData data = response.getData();

                // Convert amount back from kobo
                BigDecimal amount = data.getAmount()
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                VerifyPaymentResponse.TransactionData transactionData = VerifyPaymentResponse.TransactionData.builder()
                        .id(data.getId())
                        .status(data.getStatus().toUpperCase())
                        .reference(data.getReference())
                        .amount(amount)
                        .currency(data.getCurrency())
                        .gatewayResponse(data.getGatewayResponse())
                        .channel(data.getChannel())
                        .customer(data.getCustomer())
                        .ipAddress(data.getIpAddress())
                        .createdAt(data.getCreatedAt())
                        .paidAt(data.getPaidAt())
                        .build();

                log.info("Paystack payment verified: {} | status={}", reference, data.getStatus());
                return VerifyPaymentResponse.builder()
                        .success(true)
                        .provider("PAYSTACK")
                        .data(transactionData)
                        .build();
            }

            log.warn("Paystack verification failed: {} | response success: {}",
                    reference, response != null ? response.isSuccess() : "null");
            return VerifyPaymentResponse.builder()
                    .success(false)
                    .provider("PAYSTACK")
                    .build();

        } catch (WebClientResponseException e) {
            log.error("Paystack verification error [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return VerifyPaymentResponse.builder()
                    .success(false)
                    .provider("PAYSTACK")
                    .build();
        } catch (Exception e) {
            log.error("Error verifying Paystack payment: {}", e.getMessage(), e);
            return VerifyPaymentResponse.builder()
                    .success(false)
                    .provider("PAYSTACK")
                    .build();
        }
    }

    @Override
    public String getProviderName() {
        return "PAYSTACK";
    }

    @Override
    public boolean supportsCurrency(String currency) {
        return SUPPORTED_CURRENCIES.contains(currency != null ? currency.toUpperCase() : "");
    }

    /**
     * Normalize currency - default to NGN if null/empty or unsupported
     */
    private String normalizeCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            log.warn("Currency is null/empty, using default: {}", defaultCurrency);
            return defaultCurrency;
        }

        String normalized = currency.trim().toUpperCase();

        if (!SUPPORTED_CURRENCIES.contains(normalized)) {
            log.warn("Unsupported currency: {}. Using default: {}", currency, defaultCurrency);
            return defaultCurrency;
        }

        return normalized;
    }

    /**
     * Extract user-friendly error message from Paystack error response
     */
    private String extractErrorMessage(String errorBody) {
        try {
            // Simple extraction - you can use Jackson ObjectMapper for better parsing
            if (errorBody.contains("\"message\"")) {
                int start = errorBody.indexOf("\"message\":\"") + 11;
                int end = errorBody.indexOf("\"", start);
                if (start > 10 && end > start) {
                    String message = errorBody.substring(start, end);

                    // Add helpful context for common errors
                    if (message.contains("Currency not supported")) {
                        return "Currency not enabled on your Paystack account. Please enable NGN, USD, GHS, or ZAR in your Paystack dashboard.";
                    }
                    return message;
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract error message from: {}", errorBody);
        }
        return "Payment initialization failed. Please try again.";
    }

    private WebClient createWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}