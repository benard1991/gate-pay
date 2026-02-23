package com.gatepay.paymentservice.helper;

import com.gatepay.paymentservice.dto.PaymentRequest;
import com.gatepay.paymentservice.exception.DuplicateReferenceException;
import com.gatepay.paymentservice.repository.PaymentTransactionRepository;
import com.gatepay.paymentservice.util.PaymentReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestHelper {

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentReferenceGenerator referenceGenerator;

    /**
     * Prepare payment request - set defaults and generate reference
     */
    public void prepareRequest(PaymentRequest request) {
        // Default currency
        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            request.setCurrency("NGN");
            log.debug("Currency defaulted to NGN");
        }

        // Generate reference if not provided
        if (request.getReference() == null || request.getReference().isBlank()) {
            request.setReference(referenceGenerator.generateReference());
            log.info("Generated payment reference: {}", request.getReference());
        }
    }

    /**
     * Validate reference is unique
     * @throws DuplicateReferenceException
     */
    public void validateReferenceUnique(String reference) {
        if (transactionRepository.findByReference(reference).isPresent()) {
            log.warn("Duplicate transaction reference: {}", reference);
            throw new DuplicateReferenceException(
                    "Transaction reference already exists: " + reference
            );
        }
    }
}
