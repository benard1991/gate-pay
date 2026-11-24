package com.gatepay.kycservice.service;

import com.gatepay.kycservice.model.KycDocument;
import org.mockito.Spy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
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
import com.gatepay.kycservice.util.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycServiceImplTest {

    @Mock private KycRepository kycRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private UserClient userClient;
    @Mock private KycMapper kycMapper;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private KycNotificationProducer kycNotificationProducer;
    @Mock private KycAuditTrailRepository auditTrailRepository;
    @Mock private KycAuditMapper kycAuditMapper;

    // runs the callback like a real transaction, without a database
    @Spy private TransactionTemplate transactionTemplate = new TransactionTemplate(new AbstractPlatformTransactionManager() {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition def) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    });

    @InjectMocks
    private KycServiceImpl kycService;

    private static final Long USER_ID = 1L;
    private static final Long KYC_ID = 10L;
    private static final String IP_ADDRESS = "127.0.0.1";
    private static final String NIN = "12345678901";
    private static final String BVN = "22200000001";

    private UserDto activeUser() {
        UserDto u = new UserDto();
        u.setId(USER_ID);
        u.setFirstName("John");
        u.setLastName("Doe");
        u.setEmail("john@example.com");
        u.setStatus(AccountStatus.ACTIVE);
        return u;
    }

    private ApiResponse<UserDto> userResponse(UserDto user) {
        return new ApiResponse<>(200, "success", user);
    }

    private KycRequestUploadDto uploadDto() {
        return KycRequestUploadDto.builder()
                .userId(USER_ID)
                .nin(NIN)
                .bvn(BVN)
                .comments("Initial submission")
                .documents(new ArrayList<>())
                .build();
    }

    private KycRequest pendingKycRequest() {
        return KycRequest.builder()
                .id(KYC_ID)
                .userId(USER_ID)
                .nin(NIN)
                .bvn(BVN)
                .status(KycStatus.PENDING)
                .documents(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private KycRequestDto kycRequestDto() {
        return KycRequestDto.builder()
                .id(KYC_ID)
                .userId(USER_ID)
                .status(KycStatus.PENDING)
                .build();
    }

    @Nested
    class GetKycByUserId {

        @Test
        void returnsDto_whenKycExists() {
            KycRequest entity = pendingKycRequest();
            KycRequestDto expected = kycRequestDto();

            when(kycRepository.findByUserId(USER_ID)).thenReturn(Optional.of(entity));
            when(kycMapper.toDto(entity)).thenReturn(expected);

            assertThat(kycService.getKycByUserId(USER_ID)).isEqualTo(expected);
        }

        @Test
        void throwsUserNotFoundException_whenNoKycFound() {
            when(kycRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> kycService.getKycByUserId(USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class CreateKycRequest {

        @Test
        void createsKycRequest_happyPath() {
            try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {

                when(userClient.getUserProfile(USER_ID)).thenReturn(userResponse(activeUser()));
                when(kycRepository.existsByNin(NIN)).thenReturn(false);
                when(kycRepository.existsByBvn(BVN)).thenReturn(false);
                when(kycRepository.existsByUserId(USER_ID)).thenReturn(false);
                fileUtilsMock.when(() -> FileUtils.toKycDocuments(any(), any(), any()))
                        .thenReturn(Collections.emptyList());

                KycRequest saved = pendingKycRequest();
                when(kycRepository.save(any())).thenReturn(saved);
                when(kycMapper.toDto(saved)).thenReturn(kycRequestDto());
                when(kycAuditMapper.toJson(any())).thenReturn("{}");

                kycService.createKycRequest(uploadDto(), IP_ADDRESS);

                verify(kycRepository).save(any());
                verify(auditTrailRepository).save(any(KycAuditTrail.class));
            }
        }
    }

    @Nested
    class CreateKycOrdering {

        @Test
        void duplicateNinFailsBeforeUserLookupOrUpload() {
            try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
                when(kycRepository.existsByUserId(USER_ID)).thenReturn(false);
                when(kycRepository.existsByNin(NIN)).thenReturn(true);

                assertThatThrownBy(() -> kycService.createKycRequest(uploadDto(), IP_ADDRESS))
                        .isInstanceOf(KycException.class)
                        .hasMessageContaining("NIN");

                verifyNoInteractions(userClient);
                fileUtilsMock.verifyNoInteractions();
            }
        }

        @Test
        void uploadedFilesAreRemovedWhenTheSaveFails() {
            try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
                when(kycRepository.existsByUserId(USER_ID)).thenReturn(false);
                when(kycRepository.existsByNin(NIN)).thenReturn(false);
                when(kycRepository.existsByBvn(BVN)).thenReturn(false);
                when(userClient.getUserProfile(USER_ID)).thenReturn(userResponse(activeUser()));
                KycDocument uploaded = KycDocument.builder()
                        .documentLink("https://res.cloudinary.com/demo/image/upload/v1/gatepay/kyc/id.jpg").build();
                fileUtilsMock.when(() -> FileUtils.toKycDocuments(any(), any(), any()))
                        .thenReturn(List.of(uploaded));
                when(kycRepository.save(any()))
                        .thenThrow(new DataIntegrityViolationException("Duplicate entry for key uk_nin"));

                assertThatThrownBy(() -> kycService.createKycRequest(uploadDto(), IP_ADDRESS))
                        .isInstanceOf(KycException.class);

                verify(cloudinaryService).deleteByUrl(uploaded.getDocumentLink());
                verifyNoInteractions(kycNotificationProducer);
            }
        }
    }

    @Nested
    class ResubmitKyc {

        @Test
        void resubmitsKyc_happyPath() {
            try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {

                when(userClient.getUserProfile(USER_ID)).thenReturn(userResponse(activeUser()));
                when(kycRepository.existsByNin(NIN)).thenReturn(false);
                when(kycRepository.existsByBvn(BVN)).thenReturn(false);
                when(kycRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(USER_ID, KycStatus.REJECTED))
                        .thenReturn(Optional.of(pendingKycRequest()));

                fileUtilsMock.when(() -> FileUtils.toKycDocuments(any(), any(), any()))
                        .thenReturn(Collections.emptyList());

                when(kycRepository.save(any())).thenReturn(pendingKycRequest());
                when(kycMapper.toDto(any())).thenReturn(kycRequestDto());
                when(kycAuditMapper.toJson(any())).thenReturn("{}");

                kycService.resubmitKyc(uploadDto(), IP_ADDRESS);

                verify(kycRepository).save(any());
            }
        }
    }

    @Nested
    class UpdatePendingKyc {

        @Test
        void updatesPendingKyc_happyPath() {
            try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {

                when(kycRepository.findByIdAndStatus(KYC_ID, KycStatus.PENDING))
                        .thenReturn(Optional.of(pendingKycRequest()));
                when(kycRepository.existsByNin(NIN)).thenReturn(false);
                when(kycRepository.existsByBvn(BVN)).thenReturn(false);

                fileUtilsMock.when(() -> FileUtils.toKycDocuments(any(), any(), any()))
                        .thenReturn(Collections.emptyList());

                when(kycRepository.save(any())).thenReturn(pendingKycRequest());
                when(kycMapper.toDto(any())).thenReturn(kycRequestDto());
                when(userClient.getUserProfile(USER_ID)).thenReturn(userResponse(activeUser()));
                when(kycAuditMapper.toJson(any())).thenReturn("{}");

                kycService.updatePendingKyc(KYC_ID, uploadDto(), IP_ADDRESS);

                verify(kycRepository).save(any());
            }
        }
    }
}