package com.gatepay.paymentservice.provider;

import com.gatepay.paymentservice.dto.FlutterwaveDto;
import com.gatepay.paymentservice.dto.PaymentRequest;
import com.gatepay.paymentservice.dto.PaymentResponse;
import com.gatepay.paymentservice.dto.VerifyPaymentResponse;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.service.PaymentStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FlutterwaveStrategy implements PaymentStrategy {

    @Value("${flutterwave.secret-key}")
    private String secretKey;

    @Value("${flutterwave.base-url}")
    private String baseUrl;

    private static final List<String> SUPPORTED_CURRENCIES =
            List.of("NGN", "USD", "GHS", "KES", "ZAR", "UGX", "TZS");

    @Override
    public PaymentResponse initiatePayment(PaymentRequest request) {
        log.info("Initiating Flutterwave payment for reference: {}", request.getReference());

        try {
            WebClient webClient = createWebClient();

            Map<String, Object> flwRequest = new HashMap<>();
            flwRequest.put("tx_ref", request.getReference());
            flwRequest.put("amount", request.getAmount());
            flwRequest.put("currency", request.getCurrency());
            flwRequest.put("redirect_url", request.getCallbackUrl());
            flwRequest.put("payment_options", "card,banktransfer,ussd");

            Map<String, String> customer = new HashMap<>();
            customer.put("email", request.getEmail());
            customer.put("name", request.getCustomerName());
            customer.put("phonenumber", request.getPhoneNumber());
            flwRequest.put("customer", customer);

            Map<String, Object> customizations = new HashMap<>();
            customizations.put("title", "GatePay Payment");
            customizations.put("description", "Payment for " + request.getReference());
            customizations.put("logo", ""); // Optional: Add your logo URL
            flwRequest.put("customizations", customizations);

            if (request.getMetadata() != null) {
                flwRequest.put("meta", request.getMetadata());
            }

            Map<String, Object> response = webClient.post()
                    .uri("/payments")
                    .bodyValue(flwRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("Flutterwave initiation response: {}", response);

            if (response != null && "success".equals(response.get("status"))) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");

                return PaymentResponse.builder()
                        .success(true)
                        .message((String) response.get("message"))
                        .reference(request.getReference())
                        .authorizationUrl((String) data.get("link"))
                        .provider("FLUTTERWAVE")
                        .build();
            }

            log.warn("Flutterwave payment initiation failed: {}", response);
            return PaymentResponse.builder()
                    .success(false)
                    .message("Failed to initialize payment")
                    .provider("FLUTTERWAVE")
                    .build();

        } catch (WebClientResponseException e) {
            log.error("Flutterwave initiation error [{}]: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return PaymentResponse.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .provider("FLUTTERWAVE")
                    .build();
        } catch (Exception e) {
            log.error("Error initiating Flutterwave payment: {}", e.getMessage(), e);
            return PaymentResponse.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .provider("FLUTTERWAVE")
                    .build();
        }
    }

    @Override
    public VerifyPaymentResponse verifyPayment(String reference) {
        log.info("Verifying Flutterwave payment for reference: {}", reference);

        try {
            // Flutterwave uses tx_ref for verification
            FlutterwaveDto.VerifyResponse response = createWebClient()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/transactions/verify_by_reference")
                            .queryParam("tx_ref", reference)
                            .build())
                    .retrieve()
                    .bodyToMono(FlutterwaveDto.VerifyResponse.class)
                    .block();

            log.info("Flutterwave verification response - status: {}, message: {}",
                    response != null ? response.getStatus() : null,
                    response != null ? response.getMessage() : null);

            if (response != null && "success".equals(response.getStatus()) && response.getData() != null) {
                FlutterwaveDto.TransactionData data = response.getData();

                VerifyPaymentResponse.TransactionData transactionData =
                        VerifyPaymentResponse.TransactionData.builder()
                                .id(data.getId())
                                .status(data.getStatus() != null ? data.getStatus().toUpperCase() : "UNKNOWN")
                                .reference(data.getTxRef())
                                .amount(data.getAmount())
                                .currency(data.getCurrency())
                                .gatewayResponse(data.getProcessorResponse())
                                .channel(data.getPaymentType())
                                .customer(data.getCustomer() != null ? buildCustomer(data.getCustomer()) : null)
                                .ipAddress(data.getIp())
                                .createdAt(parseDateTime(data.getCreatedAt()))
                                .paidAt(parseDateTime(data.getCreatedAt())) // Flutterwave doesn't have separate paidAt
                                .build();

                log.info("Flutterwave payment verified: {} | status={}", reference, data.getStatus());

                return VerifyPaymentResponse.builder()
                        .success(true)
                        .message(response.getMessage())
                        .provider("FLUTTERWAVE")
                        .data(transactionData)
                        .build();
            }

            log.warn("Flutterwave verification failed: {} | response status: {}",
                    reference, response != null ? response.getStatus() : "null");
            return VerifyPaymentResponse.builder()
                    .success(false)
                    .message(response != null ? response.getMessage() : "Verification failed")
                    .provider("FLUTTERWAVE")
                    .build();

        } catch (WebClientResponseException e) {
            log.error("Flutterwave verification error [{}]: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return VerifyPaymentResponse.builder()
                    .success(false)
                    .message("Verification error: " + e.getMessage())
                    .provider("FLUTTERWAVE")
                    .build();
        } catch (Exception e) {
            log.error("Error verifying Flutterwave payment: {}", e.getMessage(), e);
            return VerifyPaymentResponse.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .provider("FLUTTERWAVE")
                    .build();
        }
    }

    /**
     * Build customer object from Flutterwave customer
     */
    private VerifyPaymentResponse.Customer buildCustomer(FlutterwaveDto.Customer flwCustomer) {
        if (flwCustomer == null) {
            return null;
        }

        // Split name into first and last name
        String firstName = null;
        String lastName = null;
        if (flwCustomer.getName() != null && !flwCustomer.getName().isEmpty()) {
            String[] nameParts = flwCustomer.getName().trim().split("\\s+", 2);
            firstName = nameParts[0];
            lastName = nameParts.length > 1 ? nameParts[1] : null;
        }

        return VerifyPaymentResponse.Customer.builder()
                .id(flwCustomer.getId())
                .firstName(firstName)
                .lastName(lastName)
                .email(flwCustomer.getEmail())
                .customerCode(null)
                .phone(flwCustomer.getPhoneNumber())
                .build();
    }

    /*
     * Parse Flutterwave datetime string to LocalDateTime
     * Flutterwave format: "2024-01-27T10:30:45.000Z"
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }

        try {
            // Remove 'Z' and parse
            if (dateTimeStr.endsWith("Z")) {
                dateTimeStr = dateTimeStr.substring(0, dateTimeStr.length() - 1);
            }
            return LocalDateTime.parse(dateTimeStr);
        } catch (Exception e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }


    private TransactionStatus mapFlutterwaveStatus(String flutterwaveStatus) {
        if (flutterwaveStatus == null) {
            return TransactionStatus.PENDING;
        }

        return switch (flutterwaveStatus.toLowerCase()) {
            case "successful", "success" -> TransactionStatus.SUCCESS;
            case "failed" -> TransactionStatus.FAILED;
            case "pending", "incomplete" -> TransactionStatus.PENDING;
            default -> TransactionStatus.PENDING;
        };
    }

    @Override
    public String getProviderName() {
        return "FLUTTERWAVE";
    }

    @Override
    public boolean supportsCurrency(String currency) {
        return SUPPORTED_CURRENCIES.contains(currency.toUpperCase());
    }

    /**
     * Create WebClient with Flutterwave authentication
     */
    private WebClient createWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}