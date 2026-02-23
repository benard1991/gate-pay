package com.gatepay.kycservice.service;

import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.dto.KycRequestUploadDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KycService {

    public KycRequestDto createKycRequest(KycRequestUploadDto dto,  String  ipAddress);

    KycRequestDto getKycByUserId(Long userId);


     KycRequestDto resubmitKyc(KycRequestUploadDto dto, String ipAddress);

     KycRequestDto updatePendingKyc(Long kycId, KycRequestUploadDto dto,String ipAddress);

    }
