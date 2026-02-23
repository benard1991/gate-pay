package com.gatepay.paymentservice.model;

import com.gatepay.paymentservice.model.enums.AuditAction;
import com.gatepay.paymentservice.model.enums.AuditStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_entity", columnList = "entityName, entityId"),
                @Index(name = "idx_action", columnList = "action")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Entity Details
    @Column(nullable = false, length = 50)
    private String entityName;

    @Column(nullable = false)
    private Long entityId;

    // Action and Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status = AuditStatus.SUCCESS;

    @Column(length = 100)
    private String performedBy;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> oldData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> newData;

    @Column(length = 255)
    private String narration;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
