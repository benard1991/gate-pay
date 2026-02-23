package com.gatepay.kycservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_request_id", nullable = false)
    private KycRequest kycRequest;

    private String documentType; // e.g., NIN, BVN, Passport
    private String documentName;
    private String documentLink; // file path or S3 link

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();


}
