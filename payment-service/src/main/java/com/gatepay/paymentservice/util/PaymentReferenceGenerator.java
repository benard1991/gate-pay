package com.gatepay.paymentservice.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class PaymentReferenceGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateReference() {
        return "PAY-" + UUID.randomUUID();
    }

    public String generateDateBasedReference() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = generateRandomAlphanumeric(6);
        return String.format("PAY-%s-%s", date, randomPart);
    }

    private String generateRandomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}