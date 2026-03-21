package com.gatepay.walletservice.service;


import java.util.Optional;

public interface IdempotencyService {

    /**
     * Check if a request has already been processed
     */
    Optional<String> getProcessedResponse(String userId, String idempotencyKey);

    /**
     * Store the response after successful processing
     */
    void storeResponse(String userId, String idempotencyKey, String responseJson);

    /**
     * Check if key exists without retrieving value
     */
    boolean isAlreadyProcessed(String userId, String idempotencyKey);

    /**
     * Manually invalidate a key (e.g. after reversal)
     */
    void invalidate(String userId, String idempotencyKey);
}