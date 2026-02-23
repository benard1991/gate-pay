package com.gatepay.paymentservice.model;

import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.model.enums.TransactionType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long walletId;
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceBefore;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType; // CREDIT, DEBIT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status; // SUCCESS, FAILED, REVERSED

    @Column(nullable = false, length = 100)
    private String reference;

    private String narration;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

