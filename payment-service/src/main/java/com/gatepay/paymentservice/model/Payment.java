package com.gatepay.paymentservice.model;

import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "reference")
        },
        indexes = {
                @Index(name = "idx_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider paymentProvider; // PAYSTACK, FLUTTERWAVE, WALLET

    @Column(nullable = false, length = 100, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 255)
    private String narration;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(optional = true)
    @JoinColumn(name = "wallet_id", nullable = true)
    private Wallet wallet;
}
