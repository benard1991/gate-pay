package com.gatepay.kycservice.controller;

import com.gatepay.kycservice.dto.ApiResponse;
import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.dto.PaginationResponse;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.service.AdminKycService;
import com.gatepay.kycservice.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/kyc/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminKycService adminKycService;


    @PutMapping("/{kycId}/status")
    public ResponseEntity<ApiResponse<KycRequestDto>> updateKycStatus(
            @PathVariable("kycId") Long kycId,
            @RequestParam("status") String status,
            @RequestParam(value = "comments", required = false) String comments,
            HttpServletRequest request
    ) {
        String ipAddress = IpUtil.extractClientIp(request);

        return ResponseEntity.ok(
                new ApiResponse<>(200, "KYC request updated successfully", adminKycService.updateKycStatus(kycId, status, comments,ipAddress))
        );
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<PaginationResponse<KycRequestDto>>> getAllKycRequests(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        PaginationResponse<KycRequestDto> paginationResponse = adminKycService.getAllKycRequests(page, size);

        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "KYC requests fetched successfully", paginationResponse)
        );
    }

    @GetMapping("/audit-trails")
    public ResponseEntity<ApiResponse<PaginationResponse<KycAuditTrail>>> getAuditTrails(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        PaginationResponse<KycAuditTrail> auditPagination = adminKycService.getAllAuditTrails(page, size);

        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "KYC audit trails fetched successfully", auditPagination)
        );
    }


    @GetMapping("/audit-trail/{kycId}")
    public ResponseEntity<ApiResponse<PaginationResponse<KycAuditTrail>>> getAuditTrailsByKycId(
            @PathVariable("kycId") Long kycId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        PaginationResponse<KycAuditTrail> response = adminKycService.getAuditTrailsByKycId(kycId, page, size);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Audit trails fetched successfully for KYC ID " + kycId, response)
        );
    }

}
