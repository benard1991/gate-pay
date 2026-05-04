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

}