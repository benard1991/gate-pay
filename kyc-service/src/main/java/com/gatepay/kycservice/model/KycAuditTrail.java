package com.gatepay.kycservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_audit_trail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycAuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long kycId;
    private Long performedBy;
    private String performedByName;
    private String action;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String oldValue;
    @Lob
    @Column(columnDefinition = "LONGTEXT")    private String newValue;
    private String ipAddress;
    private LocalDateTime performedAt;
}
