package com.gatepay.kycservice.dto;

import com.gatepay.kycservice.enums.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRequestDto {

    private Long id;
    private Long userId;
    private KycStatus status;
    private String comments;
    private String nin;
    private String bvn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<KycDocumentDto> documents;
}
