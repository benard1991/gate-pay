package com.gatepay.walletservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.walletservice.dto.CreateWalletRequest;
import com.gatepay.walletservice.exception.WalletAlreadyExistsException;
import com.gatepay.walletservice.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycEventListenerTest {

    private static final String APPROVED = "{\"userId\":14,\"kycId\":5,\"event\":\"KYC_APPROVED\"}";

    @Mock
    private WalletService walletService;

    private KycEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new KycEventListener(walletService, new ObjectMapper());
    }

    @Test
    void createsWalletWhenUserHasNone() {
        when(walletService.walletExists(14L)).thenReturn(false);

        listener.onKycApproved(APPROVED);

        ArgumentCaptor<CreateWalletRequest> captor = ArgumentCaptor.forClass(CreateWalletRequest.class);
        verify(walletService).createWallet(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(14L);
        assertThat(captor.getValue().getCurrency()).isEqualTo("NGN");
    }

    @Test
    void skipsWhenWalletAlreadyExists() {
        when(walletService.walletExists(14L)).thenReturn(true);

        listener.onKycApproved(APPROVED);

        verify(walletService, never()).createWallet(any());
    }

    @Test
    void duplicateEventRaceIsTreatedAsSuccess() {
        // both copies passed the exists check; this one lost the insert
        when(walletService.walletExists(14L)).thenReturn(false);
        when(walletService.createWallet(any())).thenThrow(new WalletAlreadyExistsException("exists"));

        assertThatCode(() -> listener.onKycApproved(APPROVED)).doesNotThrowAnyException();
    }

    @Test
    void uniqueKeyViolationIsTreatedAsSuccess() {
        when(walletService.walletExists(14L)).thenReturn(false);
        when(walletService.createWallet(any())).thenThrow(new DataIntegrityViolationException("uk_wallets_user_id"));

        assertThatCode(() -> listener.onKycApproved(APPROVED)).doesNotThrowAnyException();
    }

    @Test
    void otherFailuresAreRethrownSoTheMessageIsRetried() {
        when(walletService.walletExists(14L)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> listener.onKycApproved(APPROVED))
                .isInstanceOf(RuntimeException.class);
    }
}
