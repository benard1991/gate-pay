package com.gatepay.userservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.userservice.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycStatusListenerTest {

    @Mock
    private AdminService adminService;

    private KycStatusListener listener;

    @BeforeEach
    void setUp() {
        listener = new KycStatusListener(adminService, new ObjectMapper());
    }

    @Test
    void approvedEventMarksUserVerified() throws Exception {
        listener.onKycStatusChanged("{\"userId\":14,\"kycId\":5,\"event\":\"KYC_APPROVED\"}");

        verify(adminService).updateKycVerified(14L, true);
    }

    @Test
    void rejectedEventMarksUserNotVerified() throws Exception {
        listener.onKycStatusChanged("{\"userId\":14,\"kycId\":5,\"event\":\"KYC_REJECTED\"}");

        verify(adminService).updateKycVerified(14L, false);
    }

    @Test
    void sameEventTwiceIsHarmless() throws Exception {
        String event = "{\"userId\":14,\"kycId\":5,\"event\":\"KYC_APPROVED\"}";

        listener.onKycStatusChanged(event);
        listener.onKycStatusChanged(event);

        // same value written twice, nothing else happens
        verify(adminService, times(2)).updateKycVerified(14L, true);
        verifyNoMoreInteractions(adminService);
    }

    @Test
    void unknownEventIsIgnored() throws Exception {
        listener.onKycStatusChanged("{\"userId\":14,\"event\":\"KYC_SOMETHING_ELSE\"}");

        verifyNoInteractions(adminService);
    }

    @Test
    void failureIsRethrownSoTheMessageIsRetried() {
        doThrow(new RuntimeException("db down")).when(adminService).updateKycVerified(anyLong(), anyBoolean());

        assertThatThrownBy(() ->
                listener.onKycStatusChanged("{\"userId\":14,\"event\":\"KYC_APPROVED\"}"))
                .hasMessageContaining("db down");
    }
}
