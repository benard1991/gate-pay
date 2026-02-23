package com.gatepay.kycservice.mapper;
import com.gatepay.kycservice.dto.KycDocumentDto;
import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.model.KycRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

@Component
public class KycMapper {

    public KycRequestDto toDto(KycRequest kyc) {
        return KycRequestDto.builder()
                .id(kyc.getId())
                .userId(kyc.getUserId())
                .nin(kyc.getNin())
                .bvn(kyc.getBvn())
                .comments(kyc.getComments())
                .status(kyc.getStatus())
                .createdAt(kyc.getCreatedAt())
                .updatedAt(kyc.getUpdatedAt())
                .documents(
                        Optional.ofNullable(kyc.getDocuments())
                                .orElse(List.of())
                                .stream()
                                .map(d -> KycDocumentDto.builder()
                                        .documentName(d.getDocumentName())
                                        .documentType(d.getDocumentType())
                                        .documentUrl(d.getDocumentLink())
                                        .build())
                                .toList()
                )
                .build();
    }
}
