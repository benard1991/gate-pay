package com.gatepay.paymentservice.client;

import com.gatepay.paymentservice.config.UserClientConfig;
import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.dto.WalletDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// No fallback on purpose: if wallet-service is down we refuse the payment
// rather than take money we may not be able to credit.
@FeignClient(name = "wallet-service", contextId = "walletClient", configuration = UserClientConfig.class)
public interface WalletClient {

    @GetMapping("/internal/wallets/user/{userId}")
    ApiResponse<WalletDto> getWalletByUserId(@PathVariable("userId") Long userId);
}
