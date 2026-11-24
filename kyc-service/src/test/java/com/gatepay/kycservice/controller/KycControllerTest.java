package com.gatepay.kycservice.controller;

import com.gatepay.kycservice.dto.KycRequestDto;
import com.gatepay.kycservice.dto.KycRequestUploadDto;
import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.service.KycService;
import com.gatepay.kycservice.util.IpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KycController.class)
@DisplayName("KYC Controller")
class KycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private KycService kycService;

    private static final String BASE_URL   = "/kyc";
    private static final Long   USER_ID    = 1L;
    private static final Long   KYC_ID     = 10L;
    private static final String IP_ADDRESS = "192.168.1.1";

    private KycRequestDto kycRequestDto(KycStatus status) {
        return KycRequestDto.builder()
                .id(KYC_ID)
                .userId(USER_ID)
                .status(status)
                .comments("test comment")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .documents(Collections.emptyList())
                .build();
    }

    private MockMultipartHttpServletRequestBuilder validMultipart(String url, Object... urlVars) {
        return multipart(url, urlVars)
                .file(new MockMultipartFile(
                        "documents", "front.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3}
                ))
                .part(new MockPart("userId", String.valueOf(USER_ID).getBytes()))
                .part(new MockPart("nin", "12345678901".getBytes()))
                .part(new MockPart("bvn", "98765432101".getBytes()));
    }

    // form fields sent as request params so @ModelAttribute binds them reliably
    private MockMultipartHttpServletRequestBuilder kycForm(String url, Long userIdInForm, Object... urlVars) {
        return (MockMultipartHttpServletRequestBuilder) multipart(url, urlVars)
                .file(new MockMultipartFile(
                        "documents", "front.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3}))
                .param("userId", String.valueOf(userIdInForm))
                .param("nin", "12345678901")
                .param("bvn", "98765432101");
    }

    @Nested
    @DisplayName("GET /kyc/{userId}")
    class GetKyc {

        @Test
        @DisplayName("returns the caller's own KYC")
        void returnsOwnKyc() throws Exception {
            when(kycService.getKycByUserId(USER_ID)).thenReturn(kycRequestDto(KycStatus.PENDING));

            mockMvc.perform(get(BASE_URL + "/" + USER_ID).header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(USER_ID));
        }

        @Test
        @DisplayName("403 for another user's KYC")
        void forbidsOtherUsersKyc() throws Exception {
            mockMvc.perform(get(BASE_URL + "/" + USER_ID).header("X-User-Id", 2L))
                    .andExpect(status().isForbidden());
            verify(kycService, never()).getKycByUserId(any());
        }

        @Test
        @DisplayName("403 without gateway identity")
        void forbidsWithoutIdentity() throws Exception {
            mockMvc.perform(get(BASE_URL + "/" + USER_ID))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Submitting KYC")
    class Submit {

        @Test
        @DisplayName("403 when creating KYC for another user")
        void forbidsCreatingForAnotherUser() throws Exception {
            mockMvc.perform(kycForm(BASE_URL + "/create", 2L).header("X-User-Id", USER_ID))
                    .andExpect(status().isForbidden());
            verify(kycService, never()).createKycRequest(any(), any());
        }

        @Test
        @DisplayName("resubmit processes the request exactly once")
        void resubmitCallsServiceOnce() throws Exception {
            when(kycService.resubmitKyc(any(), any())).thenReturn(kycRequestDto(KycStatus.PENDING));

            mockMvc.perform(kycForm(BASE_URL + "/resubmit", USER_ID).header("X-User-Id", USER_ID))
                    .andExpect(status().isOk());

            verify(kycService, times(1)).resubmitKyc(any(), any());
        }

        @Test
        @DisplayName("update edits the pending request and never creates a new one")
        void updateDoesNotCreateNewRequest() throws Exception {
            when(kycService.updatePendingKyc(eq(KYC_ID), any(), any())).thenReturn(kycRequestDto(KycStatus.PENDING));

            mockMvc.perform(kycForm(BASE_URL + "/update/{kycId}", USER_ID, KYC_ID)
                            .with(req -> { req.setMethod("PUT"); return req; })
                            .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk());

            verify(kycService, times(1)).updatePendingKyc(eq(KYC_ID), any(), any());
            verify(kycService, never()).createKycRequest(any(), any());
        }
    }
}
