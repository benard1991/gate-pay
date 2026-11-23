package com.gatepay.paymentservice.helper;

import com.gatepay.paymentservice.client.WalletClient;
import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.dto.WalletDto;
import com.gatepay.paymentservice.exception.ErrorCode;
import com.gatepay.paymentservice.exception.PaymentServiceException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletValidationHelperTest {

    @Mock
    private WalletClient walletClient;

    private WalletValidationHelper helper;

    @BeforeEach
    void setUp() {
        helper = new WalletValidationHelper(walletClient);
    }

    private static ApiResponse<WalletDto> wallet(long id, String status) {
        ApiResponse<WalletDto> response = new ApiResponse<>();
        response.setData(WalletDto.builder().id(id).userId(14L).currency("NGN").status(status).build());
        return response;
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "/internal/wallets/user/14",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((PaymentServiceException) e).getErrorCode();
    }

    @Test
    void activeWalletPasses() {
        when(walletClient.getWalletByUserId(14L)).thenReturn(wallet(7, "ACTIVE"));

        assertThat(helper.validateWalletForPayment(14L, 7L).getId()).isEqualTo(7L);
    }

    @Test
    void walletIdIsOptional() {
        when(walletClient.getWalletByUserId(14L)).thenReturn(wallet(7, "ACTIVE"));

        assertThat(helper.validateWalletForPayment(14L, null).getId()).isEqualTo(7L);
    }

    @Test
    void noWalletIsRejected() {
        when(walletClient.getWalletByUserId(14L))
                .thenThrow(new FeignException.NotFound("not found", request(), null, null));

        assertThatThrownBy(() -> helper.validateWalletForPayment(14L, 7L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WALLET_NOT_FOUND));
    }

    @Test
    void suspendedWalletIsRejected() {
        when(walletClient.getWalletByUserId(14L)).thenReturn(wallet(7, "SUSPENDED"));

        assertThatThrownBy(() -> helper.validateWalletForPayment(14L, 7L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WALLET_NOT_ACTIVE));
    }

    @Test
    void someoneElsesWalletIsRejected() {
        when(walletClient.getWalletByUserId(14L)).thenReturn(wallet(7, "ACTIVE"));

        assertThatThrownBy(() -> helper.validateWalletForPayment(14L, 99L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WALLET_MISMATCH));
    }

    @Test
    void walletServiceDownMeansNoPayment() {
        when(walletClient.getWalletByUserId(14L))
                .thenThrow(new RetryableException(-1, "Connection refused",
                        Request.HttpMethod.GET, (Long) null, request()));

        assertThatThrownBy(() -> helper.validateWalletForPayment(14L, 7L))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.WALLET_SERVICE_UNAVAILABLE));
    }
}
