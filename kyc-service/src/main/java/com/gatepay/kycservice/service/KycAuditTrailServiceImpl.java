package com.gatepay.kycservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.exception.ErrorCode;
import com.gatepay.kycservice.exception.KycServiceException;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.repository.KycAuditTrailRepository;
import com.gatepay.kycservice.service.KycAuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KycAuditTrailServiceImpl implements KycAuditTrailService {

    private final KycAuditTrailRepository auditRepo;
    private final ObjectMapper objectMapper;

    @Override
    public void logCreate(KycRequestDto newKyc, Long userId, String userName, String ipAddress) {
        saveAudit(null, newKyc, userId, userName, "CREATE", ipAddress);
    }

    @Override
    public void logUpdate(KycRequestDto oldKyc, KycRequestDto newKyc, Long userId, String userName, String ipAddress) {
        saveAudit(oldKyc, newKyc, userId, userName, "UPDATE", ipAddress);
    }

    @Override
    public void logStatusChange(KycRequestDto oldKyc, KycRequestDto newKyc, Long userId, String userName, String ipAddress) {
        saveAudit(oldKyc, newKyc, userId, userName, "STATUS_CHANGE", ipAddress);
    }

    private void saveAudit(KycRequestDto oldKyc, KycRequestDto newKyc, Long userId, String userName, String action, String ipAddress) {
        try {
            KycAuditTrail audit = KycAuditTrail.builder()
                    .kycId(newKyc.getId())
                    .performedBy(userId)
                    .performedByName(userName)
                    .action(action)
                    .oldValue(oldKyc != null ? objectMapper.writeValueAsString(oldKyc) : null)
                    .newValue(newKyc != null ? objectMapper.writeValueAsString(newKyc) : null)
                    .ipAddress(ipAddress)
                    .performedAt(LocalDateTime.now())
                    .build();

            auditRepo.save(audit);
        } catch (JsonProcessingException e) {
            throw new KycServiceException(ErrorCode.SERIALIZATION_ERROR, "Failed to serialize KYC data for audit trail", e);
        }
    }
}
