package com.gatepay.kycservice.model;

import com.gatepay.kycservice.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "kyc_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private KycStatus status = KycStatus.PENDING;

    private String comments;

    private String nin;

    private String bvn;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "kycRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KycDocument> documents;
}
