package com.gatepay.kycservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.exception.KycServiceException;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.repository.KycAuditTrailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycAuditTrailServiceImplTest {

    @Mock private KycAuditTrailRepository auditRepo;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private KycAuditTrailServiceImpl auditTrailService;

    private static final Long   USER_ID    = 1L;
    private static final String USER_NAME  = "Jane Doe";
    private static final String IP_ADDRESS = "192.168.1.1";

    private KycRequestDto kycDto(Long id, KycStatus status) {
        return KycRequestDto.builder()
                .id(id)
                .userId(USER_ID)
                .status(status)
                .build();
    }

    //  logCreate

    @Nested
    @DisplayName("logCreate")
    class LogCreate {

        @Test
        @DisplayName("saves audit trail with CREATE action and null oldValue")
        void shouldSaveAuditWithCreateActionAndNullOldValue() throws JsonProcessingException {
            KycRequestDto newKyc = kycDto(10L, KycStatus.PENDING);
            when(objectMapper.writeValueAsString(newKyc)).thenReturn("{\"id\":10}");

            auditTrailService.logCreate(newKyc, USER_ID, USER_NAME, IP_ADDRESS);

            ArgumentCaptor<KycAuditTrail> captor = ArgumentCaptor.forClass(KycAuditTrail.class);
            verify(auditRepo).save(captor.capture());

            KycAuditTrail saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo("CREATE");
            assertThat(saved.getOldValue()).isNull();
            assertThat(saved.getNewValue()).isEqualTo("{\"id\":10}");
            assertThat(saved.getKycId()).isEqualTo(10L);
            assertThat(saved.getPerformedBy()).isEqualTo(USER_ID);
            assertThat(saved.getPerformedByName()).isEqualTo(USER_NAME);
            assertThat(saved.getIpAddress()).isEqualTo(IP_ADDRESS);
            assertThat(saved.getPerformedAt()).isNotNull();
        }

        @Test
        @DisplayName("throws KycServiceException when serialization fails")
        void shouldThrowKycServiceExceptionWhenSerializationFails() throws JsonProcessingException {
            KycRequestDto newKyc = kycDto(10L, KycStatus.PENDING);
            when(objectMapper.writeValueAsString(newKyc))
                    .thenThrow(new JsonProcessingException("parse error") {});

            assertThatThrownBy(() ->
                    auditTrailService.logCreate(newKyc, USER_ID, USER_NAME, IP_ADDRESS))
                    .isInstanceOf(KycServiceException.class)
                    .hasMessageContaining("Failed to serialize KYC data for audit trail");

            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("sets kycId from the newKyc DTO id")
        void shouldSetKycIdFromNewKycDto() throws JsonProcessingException {
            KycRequestDto newKyc = kycDto(99L, KycStatus.PENDING);
            when(objectMapper.writeValueAsString(newKyc)).thenReturn("{}");

            auditTrailService.logCreate(newKyc, USER_ID, USER_NAME, IP_ADDRESS);

            ArgumentCaptor<KycAuditTrail> captor = ArgumentCaptor.forClass(KycAuditTrail.class);
            verify(auditRepo).save(captor.capture());
            assertThat(captor.getValue().getKycId()).isEqualTo(99L);
        }
    }


    //  logUpdate

    @Nested
    @DisplayName("logUpdate")
    class LogUpdate {

        @Test
        @DisplayName("saves audit trail with UPDATE action, both oldValue and newValue serialized")
        void shouldSaveAuditWithUpdateActionAndBothValues() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.PENDING);

            when(objectMapper.writeValueAsString(oldKyc)).thenReturn("{\"old\":true}");
            when(objectMapper.writeValueAsString(newKyc)).thenReturn("{\"new\":true}");

            auditTrailService.logUpdate(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS);

            ArgumentCaptor<KycAuditTrail> captor = ArgumentCaptor.forClass(KycAuditTrail.class);
            verify(auditRepo).save(captor.capture());

            KycAuditTrail saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo("UPDATE");
            assertThat(saved.getOldValue()).isEqualTo("{\"old\":true}");
            assertThat(saved.getNewValue()).isEqualTo("{\"new\":true}");
        }

        @Test
        @DisplayName("throws KycServiceException when serializing oldKyc fails")
        void shouldThrowKycServiceExceptionWhenOldKycSerializationFails() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.PENDING);

            when(objectMapper.writeValueAsString(oldKyc))
                    .thenThrow(new JsonProcessingException("fail") {});

            assertThatThrownBy(() ->
                    auditTrailService.logUpdate(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS))
                    .isInstanceOf(KycServiceException.class)
                    .hasMessageContaining("Failed to serialize KYC data for audit trail");

            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws KycServiceException when serializing newKyc fails")
        void shouldThrowKycServiceExceptionWhenNewKycSerializationFails() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.APPROVED);

            when(objectMapper.writeValueAsString(oldKyc)).thenReturn("{\"old\":true}");
            when(objectMapper.writeValueAsString(newKyc))
                    .thenThrow(new JsonProcessingException("fail") {});

            assertThatThrownBy(() ->
                    auditTrailService.logUpdate(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS))
                    .isInstanceOf(KycServiceException.class)
                    .hasMessageContaining("Failed to serialize KYC data for audit trail");

            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("persists performedBy, performedByName and ipAddress correctly")
        void shouldPersistPerformedByAndIpAddress() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.APPROVED);

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            auditTrailService.logUpdate(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS);

            ArgumentCaptor<KycAuditTrail> captor = ArgumentCaptor.forClass(KycAuditTrail.class);
            verify(auditRepo).save(captor.capture());

            KycAuditTrail saved = captor.getValue();
            assertThat(saved.getPerformedBy()).isEqualTo(USER_ID);
            assertThat(saved.getPerformedByName()).isEqualTo(USER_NAME);
            assertThat(saved.getIpAddress()).isEqualTo(IP_ADDRESS);
        }
    }

    //  logStatusChange

    @Nested
    @DisplayName("logStatusChange")
    class LogStatusChange {

        @Test
        @DisplayName("saves audit trail with STATUS_CHANGE action and both values serialized")
        void shouldSaveAuditWithStatusChangeActionAndBothValues() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.APPROVED);

            when(objectMapper.writeValueAsString(oldKyc)).thenReturn("{\"status\":\"PENDING\"}");
            when(objectMapper.writeValueAsString(newKyc)).thenReturn("{\"status\":\"APPROVED\"}");

            auditTrailService.logStatusChange(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS);

            ArgumentCaptor<KycAuditTrail> captor = ArgumentCaptor.forClass(KycAuditTrail.class);
            verify(auditRepo).save(captor.capture());

            KycAuditTrail saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo("STATUS_CHANGE");
            assertThat(saved.getOldValue()).isEqualTo("{\"status\":\"PENDING\"}");
            assertThat(saved.getNewValue()).isEqualTo("{\"status\":\"APPROVED\"}");
        }

        @Test
        @DisplayName("throws KycServiceException when serialization fails")
        void shouldThrowKycServiceExceptionWhenSerializationFails() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.APPROVED);

            when(objectMapper.writeValueAsString(oldKyc))
                    .thenThrow(new JsonProcessingException("fail") {});

            assertThatThrownBy(() ->
                    auditTrailService.logStatusChange(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS))
                    .isInstanceOf(KycServiceException.class)
                    .hasMessageContaining("Failed to serialize KYC data for audit trail");

            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("sets performedAt to a non-null timestamp")
        void shouldSetPerformedAtToNonNullTimestamp() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.REJECTED);

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            auditTrailService.logStatusChange(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS);

            ArgumentCaptor<KycAuditTrail> captor = ArgumentCaptor.forClass(KycAuditTrail.class);
            verify(auditRepo).save(captor.capture());
            assertThat(captor.getValue().getPerformedAt()).isNotNull();
        }

        @Test
        @DisplayName("saves audit trail with null ipAddress when none provided")
        void shouldSaveAuditWithNullIpAddressWhenNoneProvided() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.APPROVED);

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            auditTrailService.logStatusChange(oldKyc, newKyc, USER_ID, USER_NAME, null);

            ArgumentCaptor<KycAuditTrail> captor = ArgumentCaptor.forClass(KycAuditTrail.class);
            verify(auditRepo).save(captor.capture());
            assertThat(captor.getValue().getIpAddress()).isNull();
        }

        @Test
        @DisplayName("invokes auditRepo.save exactly once per call")
        void shouldInvokeAuditRepoSaveExactlyOnce() throws JsonProcessingException {
            KycRequestDto oldKyc = kycDto(10L, KycStatus.PENDING);
            KycRequestDto newKyc = kycDto(10L, KycStatus.APPROVED);

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            auditTrailService.logStatusChange(oldKyc, newKyc, USER_ID, USER_NAME, IP_ADDRESS);

            verify(auditRepo, times(1)).save(any(KycAuditTrail.class));
        }
    }
}