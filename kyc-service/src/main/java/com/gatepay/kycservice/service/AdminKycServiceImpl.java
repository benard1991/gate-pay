package com.gatepay.kycservice.service;

import com.gatepay.kycservice.client.UserClient;
import com.gatepay.kycservice.dto.*;
import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.exception.ErrorCode;
import com.gatepay.kycservice.exception.KycException;
import com.gatepay.kycservice.exception.KycServiceException;
import com.gatepay.kycservice.exception.UserNotFoundException;
import com.gatepay.kycservice.mapper.KycAuditMapper;
import com.gatepay.kycservice.mapper.KycMapper;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.model.KycRequest;
import com.gatepay.kycservice.producer.KycNotificationProducer;
import com.gatepay.kycservice.repository.KycAuditTrailRepository;
import com.gatepay.kycservice.repository.KycRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminKycServiceImpl implements AdminKycService {

    private final KycRepository kycRepository;
    private final CloudinaryService cloudinaryService;
    private final UserClient userClient;
    private final KycMapper kycMapper;
    private final KycNotificationProducer kycNotificationProducer;
    private final KycAuditTrailRepository auditTrailRepository;
    private final KycAuditMapper kycAuditMapper;

    @Override
    @Transactional
    // cache is keyed by user id, not kyc id, so take it from the returned dto
    @Caching(evict = {
            @CacheEvict(value = "kycByUserId", key = "#result.userId"),
            @CacheEvict(value = "kycAllRequests", allEntries = true),
            @CacheEvict(value = "auditTrails", allEntries = true)
    })
    public KycRequestDto updateKycStatus(Long kycId, String statusStr, String comments, String ipAddress) {

        KycRequest kycRequest = kycRepository.findById(kycId)
                .orElseThrow(() ->
                        new UserNotFoundException("KYC request not found for id=" + kycId)
                );

        KycRequestDto oldKycDto = kycMapper.toDto(kycRequest);

        KycStatus status;
        try {
            status = KycStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new KycException("Invalid KYC status: " + statusStr);
        }

        kycRequest.setStatus(status);

        if (comments != null && !comments.isEmpty()) {
            kycRequest.setComments(comments);
        }

        kycRequest.setUpdatedAt(LocalDateTime.now());
        KycRequest saved = kycRepository.save(kycRequest);

        KycRequestDto kycDto = kycMapper.toDto(saved);

        // AUDIT TRAIL
        try {
            UserDto user = userClient
                    .getUserProfile(saved.getUserId())
                    .getData();

            KycAuditTrail audit = KycAuditTrail.builder()
                    .kycId(saved.getId())
                    .performedBy(saved.getUserId())
                    .performedByName(user != null ? user.getFirstName() : "SYSTEM")
                    .action("UPDATE_STATUS")
                    .oldValue(kycAuditMapper.toJson(oldKycDto))
                    .newValue(kycAuditMapper.toJson(kycDto))
                    .ipAddress(ipAddress)
                    .performedAt(LocalDateTime.now())
                    .build();

            auditTrailRepository.save(audit);

        } catch (Exception e) {
            throw new KycServiceException(
                    ErrorCode.AUDIT_FAILURE,
                    "Failed to record audit trail: " + e.getMessage()
            );
        }

        // NOTIFICATION
        ApiResponse<UserDto> userResponse =
                userClient.getUserProfile(saved.getUserId());

        UserDto user = userResponse.getData();

        if (user != null && user.getEmail() != null) {
            String subject = "KYC Status Updated";
            String body = String.format(
                    "Dear %s,%n%nYour KYC request (ID: %d) status has been updated to: %s.%n%s%nThank you.",
                    user.getFirstName(),
                    kycDto.getId(),
                    status.name(),
                    (comments != null ? "Comments: " + comments : "")
            );

            NotificationMessage message = new NotificationMessage(
                    user.getEmail(),
                    "EMAIL",
                    subject,
                    body,
                    Map.of("userId", kycDto.getUserId(), "kycId", kycDto.getId())
            );

            kycNotificationProducer.sendKycNotification(message);
        }

        // Update user-service
        try {
            boolean kycVerified = status == KycStatus.APPROVED;

            userClient.updateKycVerified(saved.getUserId(), kycVerified);

            if (kycVerified) {
                kycNotificationProducer.sendKycApprovedEvent(saved.getUserId());
            }

        } catch (Exception ex) {
            log.error("Failed to update user-service kycVerified | userId={} | error={}",
                    saved.getUserId(), ex.getMessage(), ex);
        }

        return kycDto;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "kycAllRequests", key = "#page + '-' + #size")
    public PaginationResponse<KycRequestDto> getAllKycRequests(int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<KycRequest> kycPage = kycRepository.findAll(pageable);

        List<KycRequestDto> kycDtos = kycPage.stream()
                .map(kycMapper::toDto)
                .collect(Collectors.toList());

        return new PaginationResponse<>(
                kycPage.getSize(),
                kycPage.getNumber(),
                kycPage.getTotalPages(),
                kycPage.getTotalElements(),
                kycDtos
        );
    }

    @Override
    @Cacheable(value = "auditTrails", key = "'all_'+#page+'_'+#size")
    public PaginationResponse<KycAuditTrail> getAllAuditTrails(Integer page, Integer size) {

        try {
            int pageNumber = (page != null && page >= 0) ? page : 0;
            int pageSize = (size != null && size > 0) ? size : 10;

            PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by("performedAt").descending());
            Page<KycAuditTrail> auditPage = auditTrailRepository.findAll(pageRequest);

            return new PaginationResponse<>(
                    auditPage.getSize(),
                    auditPage.getNumber(),
                    auditPage.getTotalPages(),
                    auditPage.getTotalElements(),
                    auditPage.getContent()
            );

        } catch (Exception e) {
            throw new KycServiceException(
                    ErrorCode.AUDIT_FAILURE,
                    "Failed to fetch audit trails: " + e.getMessage()
            );
        }
    }

    @Override
    @Cacheable(value = "auditTrails", key = "'kyc_'+#kycId+'_'+#page+'_'+#size")
    public PaginationResponse<KycAuditTrail> getAuditTrailsByKycId(Long kycId, Integer page, Integer size) {

        try {
            int pageNumber = (page != null && page >= 0) ? page : 0;
            int pageSize = (size != null && size > 0) ? size : 10;

            PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by("performedAt").descending());
            Page<KycAuditTrail> auditPage =
                    auditTrailRepository.findByKycIdOrderByPerformedAtDesc(kycId, pageRequest);

            return new PaginationResponse<>(
                    auditPage.getSize(),
                    auditPage.getNumber(),
                    auditPage.getTotalPages(),
                    auditPage.getTotalElements(),
                    auditPage.getContent()
            );

        } catch (Exception e) {
            throw new KycServiceException(
                    ErrorCode.AUDIT_FAILURE,
                    "Failed to fetch audit trails for KYC ID " + kycId + ": " + e.getMessage()
            );
        }
    }
}