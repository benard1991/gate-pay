package com.gatepay.walletservice.controller;

import com.gatepay.walletservice.dto.WalletResponse;
import com.gatepay.walletservice.enums.WalletStatus;
import com.gatepay.walletservice.exception.WalletNotFoundException;
import com.gatepay.walletservice.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalWalletController.class)
class InternalWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    @Test
    void returnsTheUsersWallet() throws Exception {
        when(walletService.getWalletByUserId(14L)).thenReturn(WalletResponse.builder()
                .id(7L).userId(14L).balance(new BigDecimal("7000.00"))
                .currency("NGN").status(WalletStatus.ACTIVE).build());

        // no X-User-Id needed: this is only called by other services, never through the gateway
        mockMvc.perform(get("/internal/wallets/user/14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void returns404WhenTheUserHasNoWallet() throws Exception {
        when(walletService.getWalletByUserId(99L))
                .thenThrow(new WalletNotFoundException("Wallet not found for user: 99"));

        mockMvc.perform(get("/internal/wallets/user/99"))
                .andExpect(status().isNotFound());
    }
}
