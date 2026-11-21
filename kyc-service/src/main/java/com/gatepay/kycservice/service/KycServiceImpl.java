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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

        ApiResponse<UserDto> userResponse = userClient.getUserProfile(dto.getUserId());
        UserDto user = userResponse.getData();

        if (user == null) {
            throw new UserNotFoundException("User with ID " + dto.getUserId() + " does not exist");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException("User account is " + user.getStatus());
        }

        if (kycRepository.existsByUserId(dto.getUserId())) {
            throw new KycException("You already have an existing KYC request");
        }

        if (dto.getNin() != null && kycRepository.existsByNin(dto.getNin())) {
            throw new KycException("This NIN is already associated with another user");
        }

        if (dto.getBvn() != null && kycRepository.existsByBvn(dto.getBvn())) {
            throw new KycException("This BVN is already associated with another user");
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

        List<KycDocument> documents = FileUtils.toKycDocuments(dto.getDocuments(), kycRequest, cloudinaryService);
        kycRequest.setDocuments(documents);

        try {
            KycRequest saved = kycRepository.save(kycRequest);
            KycRequestDto kycDto = kycMapper.toDto(saved);

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

        } catch (DataIntegrityViolationException e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("nin")) {
                throw new KycException("This NIN is already associated with another user");
            } else if (message.contains("bvn")) {
                throw new KycException("This BVN is already associated with another user");
            }
            throw new KycException("Duplicate KYC data detected");
        }
    }
    @Override
    @Transactional
    @CacheEvict(value = "kycByUserId", key = "#dto.userId")
    public KycRequestDto resubmitKyc(KycRequestUploadDto dto, String ipAddress) {

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

        kycRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(dto.getUserId(), KycStatus.REJECTED)
                .orElseThrow(() -> new KycException("No rejected KYC found to resubmit"));

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

        KycRequest saved = kycRepository.save(newKyc);
        KycRequestDto kycDto = kycMapper.toDto(saved);

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

        NotificationMessage message = new NotificationMessage(
                user.getEmail(),
                "EMAIL",
                "KYC Resubmission Successful",
                String.format("Dear %s, your KYC has been resubmitted successfully.", user.getFirstName()),
                Map.of("userId", kycDto.getUserId(), "kycId", kycDto.getId())
        );

        kycNotificationProducer.sendKycNotification(message);

        return kycDto;
    }

    @Override
    @Transactional
    @CacheEvict(value = "kycByUserId", key = "#kycId")
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