package com.gatepay.kycservice.service;

import com.gatepay.kycservice.client.UserClient;
import com.gatepay.kycservice.dto.*;
import com.gatepay.kycservice.enums.AccountStatus;
import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.exception.AccountDisabledException;
import com.gatepay.kycservice.exception.KycException;
import com.gatepay.kycservice.exception.UserNotFoundException;
import com.gatepay.kycservice.mapper.KycAuditMapper;
import com.gatepay.kycservice.mapper.KycMapper;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.model.KycDocument;
import com.gatepay.kycservice.model.KycRequest;
import com.gatepay.kycservice.producer.KycNotificationProducer;
import com.gatepay.kycservice.repository.KycAuditTrailRepository;
import com.gatepay.kycservice.repository.KycRepository;
import com.gatepay.kycservice.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private final KycRepository kycRepository;
    private final CloudinaryService cloudinaryService;
    private final UserClient userClient;
    private final KycMapper kycMapper;
    private final RabbitTemplate rabbitTemplate;
    private final KycNotificationProducer kycNotificationProducer;
    private final KycAuditTrailRepository auditTrailRepository;
    private final KycAuditMapper kycAuditMapper;
    private final TransactionTemplate transactionTemplate;


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "kycByUserId", key = "#userId")
    public KycRequestDto getKycByUserId(Long userId) {
        KycRequest kycRequest = kycRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("No KYC request found for userId=" + userId));
        return kycMapper.toDto(kycRequest);
    }


    @Override
    @Caching(evict = {
            @CacheEvict(value = "kycByUserId", key = "#dto.userId"),
            @CacheEvict(value = "kycAllRequests", allEntries = true),
            @CacheEvict(value = "auditTrails", allEntries = true)
    })
    public KycRequestDto createKycRequest(KycRequestUploadDto dto, String ipAddress) {
        long start = System.currentTimeMillis();

        // cheap local checks first, so a bad request fails before any remote call or upload
        if (kycRepository.existsByUserId(dto.getUserId())) {
            throw new KycException("You already have an existing KYC request");
        }
        checkNinAndBvnAreFree(dto);
        UserDto user = activeUser(dto.getUserId(), "User with ID " + dto.getUserId() + " does not exist");
        long checked = System.currentTimeMillis();

        KycRequest kycRequest = KycRequest.builder()
                .userId(dto.getUserId())
                .nin(dto.getNin())
                .bvn(dto.getBvn())
                .comments(dto.getComments())
                .status(KycStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // upload before opening a transaction so no DB connection is held while Cloudinary works
        List<KycDocument> documents = FileUtils.toKycDocuments(dto.getDocuments(), kycRequest, cloudinaryService);
        kycRequest.setDocuments(documents);
        long uploaded = System.currentTimeMillis();

        KycRequestDto kycDto;
        try {
            kycDto = transactionTemplate.execute(status ->
                    saveWithAudit(kycRequest, "CREATE", user.getFirstName(), ipAddress));
        } catch (DataIntegrityViolationException e) {
            deleteUploaded(documents);
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("nin")) {
                throw new KycException("This NIN is already associated with another user");
            } else if (message.contains("bvn")) {
                throw new KycException("This BVN is already associated with another user");
            }
            throw new KycException("Duplicate KYC data detected");
        } catch (RuntimeException e) {
            deleteUploaded(documents);
            throw e;
        }

        log.info("KYC request {} created for userId={} | checks {} ms, upload {} ms, save {} ms",
                kycDto.getId(), dto.getUserId(), checked - start, uploaded - checked,
                System.currentTimeMillis() - uploaded);

        kycNotificationProducer.sendKycNotification(new NotificationMessage(
                user.getEmail(),
                "EMAIL",
                "KYC Request Submitted",
                "Dear " + user.getFirstName()
                        + ",\n\nYour KYC request has been successfully submitted and is pending review.\n\nThank you.",
                Map.of("userId", kycDto.getUserId(), "kycId", kycDto.getId())
        ));
        return kycDto;
    }
    @Override
    @Caching(evict = {
            @CacheEvict(value = "kycByUserId", key = "#dto.userId"),
            @CacheEvict(value = "kycAllRequests", allEntries = true),
            @CacheEvict(value = "auditTrails", allEntries = true)
    })
    public KycRequestDto resubmitKyc(KycRequestUploadDto dto, String ipAddress) {
        long start = System.currentTimeMillis();

        // cheap local checks first
        if (kycRepository.existsByUserIdAndStatus(dto.getUserId(), KycStatus.PENDING)) {
            throw new KycException("You already have a pending KYC request");
        }
        checkNinAndBvnAreFree(dto);
        kycRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(dto.getUserId(), KycStatus.REJECTED)
                .orElseThrow(() -> new KycException("No rejected KYC found to resubmit"));
        UserDto user = activeUser(dto.getUserId(), "User with ID " + dto.getUserId() + " not found");
        long checked = System.currentTimeMillis();

        KycRequest newKyc = KycRequest.builder()
                .userId(dto.getUserId())
                .nin(dto.getNin())
                .bvn(dto.getBvn())
                .comments(dto.getComments())
                .status(KycStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<KycDocument> documents = FileUtils.toKycDocuments(dto.getDocuments(), newKyc, cloudinaryService);
        newKyc.setDocuments(documents);
        long uploaded = System.currentTimeMillis();

        KycRequestDto kycDto;
        try {
            kycDto = transactionTemplate.execute(status ->
                    saveWithAudit(newKyc, "RESUBMIT", user.getFirstName() + " " + user.getLastName(), ipAddress));
        } catch (RuntimeException e) {
            deleteUploaded(documents);
            throw e;
        }

        log.info("KYC request {} resubmitted for userId={} | checks {} ms, upload {} ms, save {} ms",
                kycDto.getId(), dto.getUserId(), checked - start, uploaded - checked,
                System.currentTimeMillis() - uploaded);

        kycNotificationProducer.sendKycNotification(new NotificationMessage(
                user.getEmail(),
                "EMAIL",
                "KYC Resubmission Successful",
                String.format("Dear %s, your KYC has been resubmitted successfully.", user.getFirstName()),
                Map.of("userId", kycDto.getUserId(), "kycId", kycDto.getId())
        ));
        return kycDto;
    }

    private void checkNinAndBvnAreFree(KycRequestUploadDto dto) {
        if (dto.getNin() != null && kycRepository.existsByNin(dto.getNin())) {
            throw new KycException("This NIN is already associated with another user");
        }
        if (dto.getBvn() != null && kycRepository.existsByBvn(dto.getBvn())) {
            throw new KycException("This BVN is already associated with another user");
        }
    }

    private UserDto activeUser(Long userId, String notFoundMessage) {
        UserDto user = userClient.getUserProfile(userId).getData();
        if (user == null) {
            throw new UserNotFoundException(notFoundMessage);
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException("User account is " + user.getStatus());
        }
        return user;
    }

    // runs inside the caller's transaction
    private KycRequestDto saveWithAudit(KycRequest kycRequest, String action, String performedByName, String ipAddress) {
        KycRequest saved = kycRepository.save(kycRequest);
        KycRequestDto kycDto = kycMapper.toDto(saved);
        auditTrailRepository.save(KycAuditTrail.builder()
                .kycId(saved.getId())
                .performedBy(saved.getUserId())
                .performedByName(performedByName)
                .action(action)
                .oldValue(null)
                .newValue(kycAuditMapper.toJson(kycDto))
                .ipAddress(ipAddress)
                .performedAt(LocalDateTime.now())
                .build());
        return kycDto;
    }

    // the request wasn't saved, so its files would be orphaned in Cloudinary
    private void deleteUploaded(List<KycDocument> documents) {
        documents.forEach(doc -> cloudinaryService.deleteByUrl(doc.getDocumentLink()));
    }

    @Override
    @Transactional
    // cache is keyed by user id, not kyc id, so take it from the returned dto
    @Caching(evict = {
            @CacheEvict(value = "kycByUserId", key = "#result.userId"),
            @CacheEvict(value = "kycAllRequests", allEntries = true),
            @CacheEvict(value = "auditTrails", allEntries = true)
    })
    public KycRequestDto updatePendingKyc(Long kycId, KycRequestUploadDto dto, String ipAddress) {

        KycRequest kyc = kycRepository.findByIdAndStatus(kycId, KycStatus.PENDING)
                .orElseThrow(() -> new KycException(
                        "KYC request cannot be updated. Only PENDING requests are editable."
                ));

        // someone else's KYC gets the same answer as a missing one
        if (!kyc.getUserId().equals(dto.getUserId())) {
            throw new KycException(
                    "KYC request cannot be updated. Only PENDING requests are editable.");
        }

        if (dto.getNin() != null && kycRepository.existsByNin(dto.getNin())) {
            throw new KycException("This NIN is already associated with another user");
        }

        if (dto.getBvn() != null && kycRepository.existsByBvn(dto.getBvn())) {
            throw new KycException("This BVN is already associated with another user");
        }

        if (dto.getNin() != null) kyc.setNin(dto.getNin());
        if (dto.getBvn() != null) kyc.setBvn(dto.getBvn());
        if (dto.getComments() != null) kyc.setComments(dto.getComments());
        kyc.setUpdatedAt(LocalDateTime.now());

        if (dto.getDocuments() != null && !dto.getDocuments().isEmpty()) {
            kyc.getDocuments().clear();
            List<KycDocument> documents = FileUtils.toKycDocuments(dto.getDocuments(), kyc, cloudinaryService);
            kyc.getDocuments().addAll(documents);
        }

        KycRequest saved = kycRepository.save(kyc);
        KycRequestDto kycDto = kycMapper.toDto(saved);

        UserDto user = userClient.getUserProfile(saved.getUserId()).getData();

        KycAuditTrail audit = KycAuditTrail.builder()
                .kycId(saved.getId())
                .performedBy(saved.getUserId())
                .performedByName(user.getFirstName())
                .action("UPDATE_PENDING")
                .oldValue(kycAuditMapper.toJson(kycMapper.toDto(kyc)))
                .newValue(kycAuditMapper.toJson(kycDto))
                .ipAddress(ipAddress)
                .performedAt(LocalDateTime.now())
                .build();

        auditTrailRepository.save(audit);

        NotificationMessage message = new NotificationMessage(
                user.getEmail(),
                "EMAIL",
                "KYC Request Updated",
                String.format(
                        "Dear %s,%n%nYour KYC request has been successfully updated and is pending review.%n%nThank you.",
                        user.getFirstName()
                ),
                Map.of("userId", kycDto.getUserId(), "kycId", kycDto.getId())
        );

        kycNotificationProducer.sendKycNotification(message);

        return kycDto;
    }
}