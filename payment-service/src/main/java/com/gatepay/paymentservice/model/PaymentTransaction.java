package com.gatepay.paymentservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(name = "idx_reference", columnList = "reference"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_user_id", columnList = "userId")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;              // Merchant reference

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;      // PAYSTACK, FLUTTERWAVE, WALLET

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;      // PENDING, SUCCESS, FAILED

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency;

    @Column(nullable = false)
    private Long userId;

//    @ManyToOne(optional = true)
//    @JoinColumn(name = "wallet_id", nullable = true)
//    private Long walletId;

    @Column(length = 100)
    private String customerEmail;

    @Column(length = 100)
    private String customerName;

    @Column(length = 20)
    private String phoneNumber;

    private String authorizationUrl;       // For Paystack/Flutterwave

    private String accessCode;             // For Paystack

    private String providerTransactionId;  // Transaction ID from the provider

    private String gatewayResponse;        // Response from the payment gateway

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    private String ipAddress;

    private LocalDateTime paidAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
