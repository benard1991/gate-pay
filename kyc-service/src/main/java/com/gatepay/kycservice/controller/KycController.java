package com.gatepay.kycservice.controller;

import com.gatepay.kycservice.dto.ApiResponse;
import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.dto.KycRequestUploadDto;
import com.gatepay.kycservice.service.KycService;
import com.gatepay.kycservice.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.gatepay.kycservice.security.CallerIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;



    @PostMapping("/create")
    public ResponseEntity<ApiResponse<KycRequestDto>> createKycRequest(
            @ModelAttribute @Valid KycRequestUploadDto uploadDto,
            HttpServletRequest request) {
        CallerIdentity.requireSelf(uploadDto.getUserId());

        String ipAddress = IpUtil.extractClientIp(request);
        KycRequestDto kycDto = kycService.createKycRequest(uploadDto, ipAddress);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED.value(), "KYC request created successfully", kycDto));
    }


    @GetMapping("/{userId}")
    public ApiResponse<KycRequestDto> getKycRequest(@PathVariable("userId") Long userId) {
        CallerIdentity.requireSelf(userId);
        KycRequestDto kycRequest = kycService.getKycByUserId(userId);
        return new ApiResponse<>(HttpStatus.OK.value(), "KYC request fetched successfully", kycRequest);
    }


    @PostMapping("/resubmit")
    public ResponseEntity<ApiResponse<KycRequestDto>> resubmitKyc(
            @ModelAttribute @Valid KycRequestUploadDto uploadDto, HttpServletRequest request) {
        CallerIdentity.requireSelf(uploadDto.getUserId());
        String ipAddress = IpUtil.extractClientIp(request);
        // previously resubmitKyc was called twice, processing every resubmission twice
        KycRequestDto kycDto = kycService.resubmitKyc(uploadDto, ipAddress);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "KYC resubmitted successfully", kycDto));
    }

    @PutMapping("/update/{kycId}")
    public ResponseEntity<ApiResponse<KycRequestDto>> updatePendingKyc(
            @PathVariable("kycId") Long kycId,
            @ModelAttribute @Valid KycRequestUploadDto uploadDto, HttpServletRequest request) {
        CallerIdentity.requireSelf(uploadDto.getUserId());
        String ipAddress = IpUtil.extractClientIp(request);
        // previously this also called createKycRequest, creating a new request on every update
        KycRequestDto kycDto = kycService.updatePendingKyc(kycId, uploadDto, ipAddress);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "KYC request updated successfully", kycDto));
    }
}
