package com.gatepay.paymentservice.service;

import com.gatepay.paymentservice.dto.PaymentRequest;
import com.gatepay.paymentservice.dto.PaymentResponse;
import com.gatepay.paymentservice.dto.VerifyPaymentResponse;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentStrategyContext {

    private final List<com.gatepay.paymentservice.service.PaymentStrategy> paymentStrategies;

    private Map<String, com.gatepay.paymentservice.service.PaymentStrategy> strategyMap;

    /**
     * Initialize strategy map
     */
    private void initializeStrategyMap() {
        if (strategyMap == null) {
            strategyMap = paymentStrategies.stream()
                    .collect(Collectors.toMap(
                            com.gatepay.paymentservice.service.PaymentStrategy::getProviderName,
                            Function.identity()
                    ));
        }
    }

    /**
     * Get strategy by provider name
     */
    public com.gatepay.paymentservice.service.PaymentStrategy getStrategy(String providerName) {
        initializeStrategyMap();

        com.gatepay.paymentservice.service.PaymentStrategy strategy = strategyMap.get(providerName.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported payment provider: " + providerName);
        }
        return strategy;
    }

    /**
     * Get strategy by provider enum
     */
    public com.gatepay.paymentservice.service.PaymentStrategy getStrategy(PaymentProvider provider) {
        return getStrategy(provider.name());
    }

    /**
     * Process payment using the specified provider
     */
    public PaymentResponse processPayment(PaymentProvider provider, PaymentRequest request) {
        log.info("Processing payment with provider: {}", provider);

        com.gatepay.paymentservice.service.PaymentStrategy strategy = getStrategy(provider);

        // Check if provider supports the currency
        if (!strategy.supportsCurrency(request.getCurrency())) {
            return PaymentResponse.builder()
                    .success(false)
                    .message("Currency " + request.getCurrency() +
                            " not supported by " + provider)
                    .provider(provider.name())
                    .build();
        }

        return strategy.initiatePayment(request);
    }

    /**
     * Verify payment using the specified provider
     */
    public VerifyPaymentResponse verifyPayment(PaymentProvider provider, String reference) {
        log.info("Verifying payment with provider: {} for reference: {}", provider, reference);

        PaymentStrategy strategy = getStrategy(provider);
        return strategy.verifyPayment(reference);
    }

    /**
     * Get all available providers
     */
    public List<String> getAvailableProviders() {
        initializeStrategyMap();
        return List.copyOf(strategyMap.keySet());
    }

    /**
     * Check if provider is available
     */
    public boolean isProviderAvailable(String providerName) {
        initializeStrategyMap();
        return strategyMap.containsKey(providerName.toUpperCase());
    }
}