package com.gatepay.kycservice.controller;

import com.gatepay.kycservice.dto.ApiResponse;
import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.dto.KycRequestUploadDto;
import com.gatepay.kycservice.service.IdempotencyService;
import com.gatepay.kycservice.service.KycService;
import com.gatepay.kycservice.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;
    private  final IdempotencyService idempotencyService;



    @PostMapping("/create")
    public ResponseEntity<ApiResponse<KycRequestDto>> createKycRequest(
            @ModelAttribute @Valid KycRequestUploadDto uploadDto,
            HttpServletRequest request) {

        String ipAddress = IpUtil.extractClientIp(request);
        KycRequestDto kycDto = kycService.createKycRequest(uploadDto, ipAddress);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED.value(), "KYC request created successfully", kycDto));
    }


    @GetMapping("/{userId}")
    public ApiResponse<KycRequestDto> getKycRequest(@PathVariable("userId") Long userId) {
        KycRequestDto kycRequest = kycService.getKycByUserId(userId);
        return new ApiResponse<>(HttpStatus.OK.value(), "KYC request fetched successfully", kycRequest);
    }


    @PostMapping("/resubmit")
    public ResponseEntity<ApiResponse<KycRequestDto>> resubmitKyc(
            @ModelAttribute @Valid KycRequestUploadDto uploadDto, HttpServletRequest request) {

        String ipAddress = IpUtil.extractClientIp(request);
        KycRequestDto kycDto = kycService.resubmitKyc(uploadDto, ipAddress);

        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "KYC resubmitted successfully",
                kycService.resubmitKyc(uploadDto, ipAddress)
                )
        );
    }

    @PutMapping("/update/{kycId}")
    public ResponseEntity<ApiResponse<KycRequestDto>> updatePendingKyc(
            @PathVariable("kycId") Long kycId,
            @ModelAttribute @Valid KycRequestUploadDto uploadDto, HttpServletRequest request)
    {
        String ipAddress = IpUtil.extractClientIp(request);
        KycRequestDto kycDto = kycService.createKycRequest(uploadDto, ipAddress);

        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "KYC request updated successfully",
                        kycService.updatePendingKyc(kycId, uploadDto, ipAddress)
                )
        );
    }

    @PostMapping("/clear-resubmit-lock/{userId}")
    public ResponseEntity<String> clearResubmitLock(@PathVariable("userId") Long userId) {
        String key = "KYC:RESUBMIT:" + userId;
        idempotencyService.clear(key);
        return ResponseEntity.ok("Resubmit lock cleared for userId=" + userId);
    }


}
