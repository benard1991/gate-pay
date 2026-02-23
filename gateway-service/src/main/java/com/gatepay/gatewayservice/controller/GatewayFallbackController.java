package com.gatepay.gatewayservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    @RequestMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status",HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "service", "AUTH",
                        "message", "Authentication service is temporarily unavailable"
                        ));
    }

    @RequestMapping("/user")
    public ResponseEntity<Map<String, Object>> userFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status",HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "service", "USER",
                        "message", "User service is temporarily unavailable"
                ));
    }

    @RequestMapping("/kyc")
    public ResponseEntity<Map<String, Object>> kycFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status",HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "service", "KYC",
                        "message", "KYC service is temporarily unavailable. Please try again later."
                ));
    }

    @RequestMapping("/notification")
    public ResponseEntity<Map<String, Object>> notificationFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status",HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "service", "NOTIFICATION",
                        "message", "Notification service is unavailable"
                ));
    }

    @RequestMapping("/payment")
    public ResponseEntity<Map<String, Object>> paymentFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status",HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "service", "PAYMENT",
                        "message", "payment service is unavailable"
                ));
    }
}
