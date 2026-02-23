package com.gatepay.kycservice.mapper;


import com.gatepay.kycservice.dto.KycRequestDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KycAuditMapper {

    private final ObjectMapper objectMapper;


    public String toJson(KycRequestDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize KYC DTO to JSON for audit trail", e);
        }
    }
}

