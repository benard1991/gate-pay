package com.gatepay.kycservice.controller;

import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.dto.PaginationResponse;
import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.service.AdminKycService;
import com.gatepay.kycservice.util.IpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@DisplayName("Admin Controller")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminKycService adminKycService;

    private static final String BASE_URL   = "/kyc/admin";
    private static final Long   KYC_ID     = 10L;
    private static final String IP_ADDRESS = "192.168.1.1";

    private KycRequestDto kycRequestDto(KycStatus status) {
        return KycRequestDto.builder()
                .id(KYC_ID)
                .userId(1L)
                .status(status)
                .comments("test comment")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .documents(Collections.emptyList())
                .build();
    }

    private PaginationResponse<KycRequestDto> kycPage(KycRequestDto dto) {
        return new PaginationResponse<>(10, 0, 1, 1L, List.of(dto));
    }

    private PaginationResponse<KycAuditTrail> auditPage(Long kycId) {
        KycAuditTrail trail = KycAuditTrail.builder()
                .id(1L)
                .kycId(kycId)
                .action("STATUS_CHANGE")
                .performedAt(LocalDateTime.now())
                .build();
        return new PaginationResponse<>(10, 0, 1, 1L, List.of(trail));
    }

    //  PUT /{kycId}/status

    @Nested
    @DisplayName("PUT /{kycId}/status — update KYC status")
    class UpdateKycStatus {

        @Test
        @DisplayName("returns 200 with updated KYC data when status is approved")
        void shouldReturn200WithUpdatedKycDataWhenStatusIsApproved() throws Exception {
            KycRequestDto dto = kycRequestDto(KycStatus.APPROVED);

            try (MockedStatic<IpUtil> ipUtil = mockStatic(IpUtil.class)) {
                ipUtil.when(() -> IpUtil.extractClientIp(any())).thenReturn(IP_ADDRESS);
                when(adminKycService.updateKycStatus(KYC_ID, "APPROVED", "Looks good", IP_ADDRESS))
                        .thenReturn(dto);

                mockMvc.perform(put(BASE_URL + "/{kycId}/status", KYC_ID)
                                .param("status", "APPROVED")
                                .param("comments", "Looks good"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value(200))
                        .andExpect(jsonPath("$.message").value("KYC request updated successfully"))
                        .andExpect(jsonPath("$.data.id").value(KYC_ID))
                        .andExpect(jsonPath("$.data.status").value("APPROVED"));
            }
        }

        @Test
        @DisplayName("returns 200 with updated KYC data when status is rejected")
        void shouldReturn200WithUpdatedKycDataWhenStatusIsRejected() throws Exception {
            KycRequestDto dto = kycRequestDto(KycStatus.REJECTED);

            try (MockedStatic<IpUtil> ipUtil = mockStatic(IpUtil.class)) {
                ipUtil.when(() -> IpUtil.extractClientIp(any())).thenReturn(IP_ADDRESS);
                when(adminKycService.updateKycStatus(KYC_ID, "REJECTED", null, IP_ADDRESS))
                        .thenReturn(dto);

                mockMvc.perform(put(BASE_URL + "/{kycId}/status", KYC_ID)
                                .param("status", "REJECTED"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("REJECTED"));
            }
        }

        @Test
        @DisplayName("works without the optional comments parameter")
        void shouldWorkWithoutTheOptionalCommentsParameter() throws Exception {
            KycRequestDto dto = kycRequestDto(KycStatus.APPROVED);

            try (MockedStatic<IpUtil> ipUtil = mockStatic(IpUtil.class)) {
                ipUtil.when(() -> IpUtil.extractClientIp(any())).thenReturn(IP_ADDRESS);
                when(adminKycService.updateKycStatus(eq(KYC_ID), eq("APPROVED"), isNull(), eq(IP_ADDRESS)))
                        .thenReturn(dto);

                mockMvc.perform(put(BASE_URL + "/{kycId}/status", KYC_ID)
                                .param("status", "APPROVED"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id").value(KYC_ID));
            }
        }

        @Test
        @DisplayName("returns 422 when the required status parameter is missing")
        void shouldReturn422WhenTheRequiredStatusParameterIsMissing() throws Exception {
            mockMvc.perform(put(BASE_URL + "/{kycId}/status", KYC_ID))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.message").value("Required parameter 'status' is missing"))
                    .andExpect(jsonPath("$.code").value("GEN_422"));
        }

        @Test
        @DisplayName("passes the extracted client IP address to the service")
        void shouldPassTheExtractedClientIpAddressToTheService() throws Exception {
            KycRequestDto dto = kycRequestDto(KycStatus.APPROVED);

            try (MockedStatic<IpUtil> ipUtil = mockStatic(IpUtil.class)) {
                ipUtil.when(() -> IpUtil.extractClientIp(any())).thenReturn("10.0.0.1");
                when(adminKycService.updateKycStatus(KYC_ID, "APPROVED", null, "10.0.0.1"))
                        .thenReturn(dto);

                mockMvc.perform(put(BASE_URL + "/{kycId}/status", KYC_ID)
                                .param("status", "APPROVED"))
                        .andExpect(status().isOk());

                verify(adminKycService).updateKycStatus(KYC_ID, "APPROVED", null, "10.0.0.1");
            }
        }

        @Test
        @DisplayName("returns JSON content type in the response")
        void shouldReturnJsonContentTypeInTheResponse() throws Exception {
            KycRequestDto dto = kycRequestDto(KycStatus.APPROVED);

            try (MockedStatic<IpUtil> ipUtil = mockStatic(IpUtil.class)) {
                ipUtil.when(() -> IpUtil.extractClientIp(any())).thenReturn(IP_ADDRESS);
                when(adminKycService.updateKycStatus(any(), any(), any(), any())).thenReturn(dto);

                mockMvc.perform(put(BASE_URL + "/{kycId}/status", KYC_ID)
                                .param("status", "APPROVED"))
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
            }
        }
    }

    //  GET /all

    @Nested
    @DisplayName("GET /all — get all KYC requests")
    class GetAllKycRequests {

        @Test
        @DisplayName("returns 200 with a paginated list of KYC requests")
        void shouldReturn200WithAPaginatedListOfKycRequests() throws Exception {
            KycRequestDto dto = kycRequestDto(KycStatus.PENDING);
            when(adminKycService.getAllKycRequests(0, 10)).thenReturn(kycPage(dto));

            mockMvc.perform(get(BASE_URL + "/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("KYC requests fetched successfully"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].id").value(KYC_ID))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.totalPages").value(1));
        }

        @Test
        @DisplayName("returns 200 with empty content when no KYC records exist")
        void shouldReturn200WithEmptyContentWhenNoKycRecordsExist() throws Exception {
            when(adminKycService.getAllKycRequests(0, 10))
                    .thenReturn(new PaginationResponse<>(10, 0, 0, 0L, Collections.emptyList()));

            mockMvc.perform(get(BASE_URL + "/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("uses default page=0 and size=10 when no query params are provided")
        void shouldUseDefaultPageAndSizeWhenNoQueryParamsAreProvided() throws Exception {
            when(adminKycService.getAllKycRequests(0, 10))
                    .thenReturn(kycPage(kycRequestDto(KycStatus.PENDING)));

            mockMvc.perform(get(BASE_URL + "/all"))
                    .andExpect(status().isOk());

            verify(adminKycService).getAllKycRequests(0, 10);
        }

        @Test
        @DisplayName("forwards custom page and size query parameters to the service")
        void shouldForwardCustomPageAndSizeQueryParametersToTheService() throws Exception {
            when(adminKycService.getAllKycRequests(2, 5))
                    .thenReturn(new PaginationResponse<>(5, 2, 0, 0L, Collections.emptyList()));

            mockMvc.perform(get(BASE_URL + "/all")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk());

            verify(adminKycService).getAllKycRequests(2, 5);
        }
    }

    //  GET /audit-trails

    @Nested
    @DisplayName("GET /audit-trails — get all audit trails")
    class GetAllAuditTrails {

        @Test
        @DisplayName("returns 200 with a paginated list of audit trails")
        void shouldReturn200WithAPaginatedListOfAuditTrails() throws Exception {
            when(adminKycService.getAllAuditTrails(0, 10)).thenReturn(auditPage(KYC_ID));

            mockMvc.perform(get(BASE_URL + "/audit-trails"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("KYC audit trails fetched successfully"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].kycId").value(KYC_ID))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 200 with empty content when no audit trails exist")
        void shouldReturn200WithEmptyContentWhenNoAuditTrailsExist() throws Exception {
            when(adminKycService.getAllAuditTrails(0, 10))
                    .thenReturn(new PaginationResponse<>(10, 0, 0, 0L, Collections.emptyList()));

            mockMvc.perform(get(BASE_URL + "/audit-trails"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }

        @Test
        @DisplayName("uses default page=0 and size=10 when no query params are provided")
        void shouldUseDefaultPageAndSizeWhenNoQueryParamsAreProvided() throws Exception {
            when(adminKycService.getAllAuditTrails(0, 10)).thenReturn(auditPage(KYC_ID));

            mockMvc.perform(get(BASE_URL + "/audit-trails"))
                    .andExpect(status().isOk());

            verify(adminKycService).getAllAuditTrails(0, 10);
        }

        @Test
        @DisplayName("forwards custom page and size query parameters to the service")
        void shouldForwardCustomPageAndSizeQueryParametersToTheService() throws Exception {
            when(adminKycService.getAllAuditTrails(1, 5))
                    .thenReturn(new PaginationResponse<>(5, 1, 0, 0L, Collections.emptyList()));

            mockMvc.perform(get(BASE_URL + "/audit-trails")
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isOk());

            verify(adminKycService).getAllAuditTrails(1, 5);
        }
    }

    //  GET /audit-trail/{kycId}

    @Nested
    @DisplayName("GET /audit-trail/{kycId} — get audit trails by KYC ID")
    class GetAuditTrailsByKycId {

        @Test
        @DisplayName("returns 200 with audit trails for the given KYC ID")
        void shouldReturn200WithAuditTrailsForTheGivenKycId() throws Exception {
            when(adminKycService.getAuditTrailsByKycId(KYC_ID, 0, 10)).thenReturn(auditPage(KYC_ID));

            mockMvc.perform(get(BASE_URL + "/audit-trail/{kycId}", KYC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message")
                            .value("Audit trails fetched successfully for KYC ID " + KYC_ID))
                    .andExpect(jsonPath("$.data.content[0].kycId").value(KYC_ID))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 200 with empty content when no audit trails exist for that KYC ID")
        void shouldReturn200WithEmptyContentWhenNoAuditTrailsExistForThatKycId() throws Exception {
            when(adminKycService.getAuditTrailsByKycId(KYC_ID, 0, 10))
                    .thenReturn(new PaginationResponse<>(10, 0, 0, 0L, Collections.emptyList()));

            mockMvc.perform(get(BASE_URL + "/audit-trail/{kycId}", KYC_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }

        @Test
        @DisplayName("uses default page=0 and size=10 when no query params are provided")
        void shouldUseDefaultPageAndSizeWhenNoQueryParamsAreProvided() throws Exception {
            when(adminKycService.getAuditTrailsByKycId(KYC_ID, 0, 10)).thenReturn(auditPage(KYC_ID));

            mockMvc.perform(get(BASE_URL + "/audit-trail/{kycId}", KYC_ID))
                    .andExpect(status().isOk());

            verify(adminKycService).getAuditTrailsByKycId(KYC_ID, 0, 10);
        }

        @Test
        @DisplayName("forwards custom page and size query parameters to the service")
        void shouldForwardCustomPageAndSizeQueryParametersToTheService() throws Exception {
            when(adminKycService.getAuditTrailsByKycId(KYC_ID, 3, 20))
                    .thenReturn(new PaginationResponse<>(20, 3, 0, 0L, Collections.emptyList()));

            mockMvc.perform(get(BASE_URL + "/audit-trail/{kycId}", KYC_ID)
                            .param("page", "3")
                            .param("size", "20"))
                    .andExpect(status().isOk());

            verify(adminKycService).getAuditTrailsByKycId(KYC_ID, 3, 20);
        }

        @Test
        @DisplayName("includes the KYC ID in the success message")
        void shouldIncludeTheKycIdInTheSuccessMessage() throws Exception {
            when(adminKycService.getAuditTrailsByKycId(KYC_ID, 0, 10)).thenReturn(auditPage(KYC_ID));

            mockMvc.perform(get(BASE_URL + "/audit-trail/{kycId}", KYC_ID))
                    .andExpect(jsonPath("$.message")
                            .value("Audit trails fetched successfully for KYC ID " + KYC_ID));
        }

        @Test
        @DisplayName("passes the correct KYC ID path variable to the service")
        void shouldPassTheCorrectKycIdPathVariableToTheService() throws Exception {
            Long differentKycId = 99L;
            when(adminKycService.getAuditTrailsByKycId(differentKycId, 0, 10))
                    .thenReturn(auditPage(differentKycId));

            mockMvc.perform(get(BASE_URL + "/audit-trail/{kycId}", differentKycId))
                    .andExpect(status().isOk());

            verify(adminKycService).getAuditTrailsByKycId(differentKycId, 0, 10);
        }
    }
}