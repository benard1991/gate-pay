package com.gatepay.kycservice.service;

import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.dto.PaginationResponse;
import com.gatepay.kycservice.model.KycAuditTrail;

public interface AdminKycService {

  PaginationResponse<KycRequestDto> getAllKycRequests(int page, int size) ;

  KycRequestDto updateKycStatus(Long kycId, String statusStr, String comments, String ipAddress);

  public PaginationResponse<KycAuditTrail> getAllAuditTrails(Integer page, Integer size);

  PaginationResponse<KycAuditTrail> getAuditTrailsByKycId(Long kycId, Integer page, Integer size);
}


