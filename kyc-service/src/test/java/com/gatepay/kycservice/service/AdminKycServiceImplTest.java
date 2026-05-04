package com.gatepay.kycservice.service;

import com.gatepay.kycservice.client.UserClient;
import com.gatepay.kycservice.dto.*;
import com.gatepay.kycservice.enums.AccountStatus;
import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.exception.*;
import com.gatepay.kycservice.mapper.KycAuditMapper;
import com.gatepay.kycservice.mapper.KycMapper;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.model.KycRequest;
import com.gatepay.kycservice.producer.KycNotificationProducer;
import com.gatepay.kycservice.repository.KycAuditTrailRepository;
import com.gatepay.kycservice.repository.KycRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminKycServiceImplTest {

    @Mock private KycRepository kycRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private UserClient userClient;
    @Mock private KycMapper kycMapper;
    @Mock private KycNotificationProducer kycNotificationProducer;
    @Mock private KycAuditTrailRepository auditTrailRepository;
    @Mock private KycAuditMapper kycAuditMapper;

    @InjectMocks
    private AdminKycServiceImpl adminKycService;

    private static final Long USER_ID = 1L;
    private static final Long KYC_ID = 10L;
    private static final String IP_ADDRESS = "192.168.1.1";

    private UserDto activeUser() {
        UserDto u = new UserDto();
        u.setId(USER_ID);
        u.setFirstName("Jane");
        u.setEmail("jane@example.com");
        u.setStatus(AccountStatus.ACTIVE);
        return u;
    }

    private ApiResponse<UserDto> userResponse(UserDto user) {
        return new ApiResponse<>(200, "success", user);
    }

    private KycRequest pendingKycRequest() {
        return KycRequest.builder()
                .id(KYC_ID)
                .userId(USER_ID)
                .status(KycStatus.PENDING)
                .comments("awaiting review")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private KycRequestDto dto(KycStatus status) {
        return KycRequestDto.builder()
                .id(KYC_ID)
                .userId(USER_ID)
                .status(status)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // updateKycStatus
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldApproveKyc_andTriggerAllSideEffects() {
        KycRequest entity = pendingKycRequest();

        when(kycRepository.findById(KYC_ID)).thenReturn(Optional.of(entity));
        when(kycMapper.toDto(entity)).thenReturn(dto(KycStatus.PENDING), dto(KycStatus.APPROVED));
        when(kycRepository.save(entity)).thenReturn(entity);
        when(userClient.getUserProfile(USER_ID))
                .thenReturn(userResponse(activeUser()))
                .thenReturn(userResponse(activeUser()));
        when(kycAuditMapper.toJson(any())).thenReturn("{}");

        KycRequestDto result =
                adminKycService.updateKycStatus(KYC_ID, "APPROVED", "ok", IP_ADDRESS);

        assertThat(result.getStatus()).isEqualTo(KycStatus.APPROVED);
        verify(auditTrailRepository).save(any());
        verify(kycNotificationProducer).sendKycNotification(any());
        verify(kycNotificationProducer).sendKycApprovedEvent(USER_ID);
        verify(userClient).updateKycVerified(USER_ID, true);
    }

    @Test
    void shouldRejectKyc_withoutWalletEvent() {
        KycRequest entity = pendingKycRequest();

        when(kycRepository.findById(KYC_ID)).thenReturn(Optional.of(entity));
        when(kycMapper.toDto(entity)).thenReturn(dto(KycStatus.PENDING), dto(KycStatus.REJECTED));
        when(kycRepository.save(entity)).thenReturn(entity);
        when(userClient.getUserProfile(USER_ID))
                .thenReturn(userResponse(activeUser()))
                .thenReturn(userResponse(activeUser()));
        when(kycAuditMapper.toJson(any())).thenReturn("{}");

        adminKycService.updateKycStatus(KYC_ID, "REJECTED", null, IP_ADDRESS);

        verify(userClient).updateKycVerified(USER_ID, false);
        verify(kycNotificationProducer, never()).sendKycApprovedEvent(any());
    }

    @Test
    void shouldThrowWhenKycNotFound() {
        when(kycRepository.findById(KYC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                adminKycService.updateKycStatus(KYC_ID, "APPROVED", null, IP_ADDRESS))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void shouldThrowForInvalidStatus() {
        when(kycRepository.findById(KYC_ID)).thenReturn(Optional.of(pendingKycRequest()));

        assertThatThrownBy(() ->
                adminKycService.updateKycStatus(KYC_ID, "INVALID", null, IP_ADDRESS))
                .isInstanceOf(KycException.class);
    }

    // ─────────────────────────────────────────────────────────────
    // getAllKycRequests
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnPagedKycRequests() {
        KycRequest entity = pendingKycRequest();

        Page<KycRequest> page = new PageImpl<>(List.of(entity));
        when(kycRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(kycMapper.toDto(entity)).thenReturn(dto(KycStatus.PENDING));

        PaginationResponse<KycRequestDto> result =
                adminKycService.getAllKycRequests(0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────
    // getAllAuditTrails
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAuditTrails() {
        KycAuditTrail trail = KycAuditTrail.builder().kycId(KYC_ID).build();

        Page<KycAuditTrail> page = new PageImpl<>(List.of(trail));
        when(auditTrailRepository.findAll(any(PageRequest.class))).thenReturn(page);

        PaginationResponse<KycAuditTrail> result =
                adminKycService.getAllAuditTrails(0, 10);

        assertThat(result.getContent()).contains(trail);
    }

    // ─────────────────────────────────────────────────────────────
    // getAuditTrailsByKycId
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAuditTrailsByKycId() {
        KycAuditTrail trail = KycAuditTrail.builder().kycId(KYC_ID).build();

        Page<KycAuditTrail> page = new PageImpl<>(List.of(trail));
        when(auditTrailRepository.findByKycIdOrderByPerformedAtDesc(eq(KYC_ID), any()))
                .thenReturn(page);

        PaginationResponse<KycAuditTrail> result =
                adminKycService.getAuditTrailsByKycId(KYC_ID, 0, 10);

        assertThat(result.getContent()).contains(trail);
    }
}