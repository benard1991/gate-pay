package com.gatepay.kycservice.service;

import com.gatepay.kycservice.client.UserClient;
import com.gatepay.kycservice.dto.*;
import com.gatepay.kycservice.enums.AccountStatus;
import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.exception.*;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private final KycRepository kycRepository;
    private final CloudinaryService cloudinaryService;
    private final UserClient userClient;
    private final KycMapper kycMapper;
    private final RabbitTemplate rabbitTemplate;
    private final  KycNotificationProducer kycNotificationProducer;
    private  final KycAuditTrailRepository auditTrailRepository;
    private  final KycAuditMapper kycAuditMapper;
    private final IdempotencyService idempotencyService;



    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "kycByUserId", key = "#userId")
    public KycRequestDto getKycByUserId(Long userId) {

        KycRequest kycRequest = kycRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("No KYC request found for userId=" + userId));

        return kycMapper.toDto(kycRequest);
    }

    @Override
    @Transactional
    @CacheEvict(value = "kycByUserId", key = "#dto.userId")
    public KycRequestDto createKycRequest(KycRequestUploadDto dto, String ipAddress) {

        // IDEMPOTENCY
        String idempotencyKey = "KYC:CREATE:" + dto.getUserId();
        idempotencyService.checkAndLock(idempotencyKey);

        try {
            ApiResponse<UserDto> userResponse = userClient.getUserProfile(dto.getUserId());
            UserDto user = userResponse.getData();

            if (user == null) {
                throw new UserNotFoundException("User with ID " + dto.getUserId() + " does not exist");
            }

            if (user.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountDisabledException("User account is " + user.getStatus());
            }

            if (dto.getNin() != null && kycRepository.existsByNin(dto.getNin())) {
                throw new KycException("This NIN is already associated with another user");
            }

            if (dto.getBvn() != null && kycRepository.existsByBvn(dto.getBvn())) {
                throw new KycException("This BVN is already associated with another user");
            }

            if (kycRepository.existsByUserIdAndStatus(dto.getUserId(), KycStatus.PENDING)) {
                throw new KycException("You already have a pending KYC request");
            }

            KycRequest kycRequest = KycRequest.builder()
                    .userId(dto.getUserId())
                    .nin(dto.getNin())
                    .bvn(dto.getBvn())
                    .comments(dto.getComments())
                    .status(KycStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // --- Handle documents using reusable FileUtils ---
            List<KycDocument> documents = FileUtils.toKycDocuments(dto.getDocuments(), kycRequest, cloudinaryService);
            kycRequest.setDocuments(documents);

            KycRequest saved = kycRepository.save(kycRequest);
            KycRequestDto kycDto = kycMapper.toDto(saved);

            // ---------- AUDIT TRAIL ----------
            KycAuditTrail audit = KycAuditTrail.builder()
                    .kycId(saved.getId())
                    .performedBy(saved.getUserId())
                    .performedByName(user.getFirstName())
                    .action("CREATE")
                    .oldValue(null)
                    .newValue(kycAuditMapper.toJson(kycDto))
                    .ipAddress(ipAddress)
                    .performedAt(LocalDateTime.now())
                    .build();

            auditTrailRepository.save(audit);

            //  NOTIFICATION
            NotificationMessage message = new NotificationMessage(
                    user.getEmail(),
                    "EMAIL",
                    "KYC Request Submitted",
                    "Dear " + user.getFirstName()
                            + ",\n\nYour KYC request has been successfully submitted and is pending review.\n\nThank you.",
                    Map.of("userId", kycDto.getUserId(), "kycId", kycDto.getId())
            );

            kycNotificationProducer.sendKycNotification(message);

            return kycDto;

        } catch (Exception ex) {
            // Optional but recommended: free Redis key on failure
            idempotencyService.clear(idempotencyKey);
            throw ex;
        }
    }

    @Transactional
    @CacheEvict(value = "kycByUserId", key = "#dto.userId")
    public KycRequestDto resubmitKyc(KycRequestUploadDto dto, String ipAddress) {

        String idempotencyKey = "KYC:RESUBMIT:" + dto.getUserId();

        // IDEMPOTENCY CHECK
        idempotencyService.checkAndLock(idempotencyKey);

        try {

            ApiResponse<UserDto> userResponse = userClient.getUserProfile(dto.getUserId());
            UserDto user = userResponse.getData();

            if (user == null) {
                throw new UserNotFoundException("User with ID " + dto.getUserId() + " not found");
            }

            if (user.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountDisabledException("User account is " + user.getStatus());
            }

            if (kycRepository.existsByUserIdAndStatus(dto.getUserId(), KycStatus.PENDING)) {
                throw new KycException("You already have a pending KYC request");
            }

            if (dto.getNin() != null && kycRepository.existsByNin(dto.getNin())) {
                throw new KycException("This NIN is already associated with another user");
            }

            if (dto.getBvn() != null && kycRepository.existsByBvn(dto.getBvn())) {
                throw new KycException("This BVN is already associated with another user");
            }

            // Fetch last rejected KYC
            kycRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(dto.getUserId(), KycStatus.REJECTED)
                    .orElseThrow(() -> new KycException("No rejected KYC found to resubmit"));

            // Create NEW KYC request
            KycRequest newKyc = KycRequest.builder()
                    .userId(dto.getUserId())
                    .nin(dto.getNin())
                    .bvn(dto.getBvn())
                    .comments(dto.getComments())
                    .status(KycStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // --- Handle documents using reusable FileUtils ---
            List<KycDocument> documents = FileUtils.toKycDocuments(dto.getDocuments(), newKyc, cloudinaryService);
            newKyc.setDocuments(documents);

            KycRequest saved = kycRepository.save(newKyc);
            KycRequestDto kycDto = kycMapper.toDto(saved);

            // --- AUDIT TRAIL ---
            KycAuditTrail audit = KycAuditTrail.builder()
                    .kycId(saved.getId())
                    .performedBy(saved.getUserId())
                    .performedByName(user.getFirstName() + " " + user.getLastName())
                    .action("RESUBMIT")
                    .oldValue(null)
                    .newValue(kycAuditMapper.toJson(kycDto))
                    .ipAddress(ipAddress)
                    .performedAt(LocalDateTime.now())
                    .build();

            auditTrailRepository.save(audit);

            // SEND NOTIFICATION
            NotificationMessage message = new NotificationMessage(
                    user.getEmail(),
                    "EMAIL",
                    "KYC Resubmission Successful",
                    String.format("Dear %s, your KYC has been resubmitted successfully.", user.getFirstName()),
                    Map.of("userId", kycDto.getUserId(), "kycId", kycDto.getId())
            );

            kycNotificationProducer.sendKycNotification(message);

            return kycDto;

        } catch (Exception ex) {
            idempotencyService.clear(idempotencyKey);
            throw ex;
        }
    }


    @Override
    @Transactional
    @CacheEvict(value = "kycByUserId", key = "#kycId")
    public KycRequestDto updatePendingKyc(Long kycId, KycRequestUploadDto dto, String ipAddress) {

        String idempotencyKey = "KYC:UPDATE_PENDING:" + kycId;
        // IDEMPOTENCY CHECK
        idempotencyService.checkAndLock(idempotencyKey);

        try {

            KycRequest kyc = kycRepository.findByIdAndStatus(kycId, KycStatus.PENDING)
                    .orElseThrow(() -> new KycException(
                            "KYC request cannot be updated. Only PENDING requests are editable."
                    ));

            // Validate NIN and BVN uniqueness
            if (dto.getNin() != null && kycRepository.existsByNin(dto.getNin())) {
                throw new KycException("This NIN is already associated with another user");
            }
            if (dto.getBvn() != null && kycRepository.existsByBvn(dto.getBvn())) {
                throw new KycException("This BVN is already associated with another user");
            }

            // Update basic fields
            if (dto.getNin() != null) kyc.setNin(dto.getNin());
            if (dto.getBvn() != null) kyc.setBvn(dto.getBvn());
            if (dto.getComments() != null) kyc.setComments(dto.getComments());
            kyc.setUpdatedAt(LocalDateTime.now());

            // Handle documents using FileUtils
            if (dto.getDocuments() != null && !dto.getDocuments().isEmpty()) {
                // Clear existing documents (orphanRemoval = true)
                kyc.getDocuments().clear();

                List<KycDocument> documents = FileUtils.toKycDocuments(dto.getDocuments(), kyc, cloudinaryService);
                kyc.getDocuments().addAll(documents);
            }

            KycRequest saved = kycRepository.save(kyc);
            KycRequestDto kycDto = kycMapper.toDto(saved);

            // --- AUDIT TRAIL ---
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

            // --- SEND NOTIFICATION ---
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

        } catch (Exception ex) {
            idempotencyService.clear(idempotencyKey);
            throw ex;
        }
    }

}
