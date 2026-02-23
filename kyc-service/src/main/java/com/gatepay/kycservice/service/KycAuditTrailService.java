package com.gatepay.kycservice.service;
import com.gatepay.kycservice.dto.KycRequestDto;

public interface KycAuditTrailService {

    void logCreate(KycRequestDto newKyc, Long userId, String userName, String ipAddress);

    void logUpdate(KycRequestDto oldKyc, KycRequestDto newKyc, Long userId, String userName, String ipAddress);

    void logStatusChange(KycRequestDto oldKyc, KycRequestDto newKyc, Long userId, String userName, String ipAddress);
}

