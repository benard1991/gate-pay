package com.gatepay.paymentservice.helper;

import com.gatepay.paymentservice.client.WalletClient;
import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.dto.WalletDto;
import com.gatepay.paymentservice.exception.ErrorCode;
import com.gatepay.paymentservice.exception.PaymentServiceException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// A successful payment is credited to the user's wallet. If there's no usable wallet
// the customer would be charged and the credit would fail, so check before we start.
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletValidationHelper {

    private final WalletClient walletClient;

    public WalletDto validateWalletForPayment(Long userId, Long walletId) {
        WalletDto wallet = fetchWallet(userId);

        if (wallet == null) {
            throw new PaymentServiceException(ErrorCode.WALLET_NOT_FOUND,
                    "You need a wallet before you can make a payment");
        }
        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new PaymentServiceException(ErrorCode.WALLET_NOT_ACTIVE,
                    "Your wallet is " + wallet.getStatus() + ", so payments can't be made into it");
        }
        // walletId is optional in the request, but if it's sent it has to be the user's own
        if (walletId != null && !walletId.equals(wallet.getId())) {
            log.warn("Wallet mismatch | userId={} | requested={} | actual={}", userId, walletId, wallet.getId());
            throw new PaymentServiceException(ErrorCode.WALLET_MISMATCH,
                    "This wallet does not belong to you");
        }
        return wallet;
    }

    private WalletDto fetchWallet(Long userId) {
        try {
            ApiResponse<WalletDto> response = walletClient.getWalletByUserId(userId);
            return response == null ? null : response.getData();
        } catch (RuntimeException e) {
            FeignException feign = findFeignException(e);
            if (feign != null && feign.status() == 404) {
                return null;
            }
            log.error("Wallet check failed, not starting payment | userId={} | error={}", userId, e.getMessage());
            throw new PaymentServiceException(ErrorCode.WALLET_SERVICE_UNAVAILABLE,
                    "We couldn't confirm your wallet right now. No payment was started, please try again.");
        }
    }

    // Feign errors can come wrapped (e.g. by a circuit breaker), so look through the causes
    private static FeignException findFeignException(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FeignException f) {
                return f;
            }
        }
        return null;
    }
}
