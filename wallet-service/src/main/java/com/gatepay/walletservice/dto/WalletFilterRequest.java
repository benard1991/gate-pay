package com.gatepay.walletservice.dto;


import com.gatepay.walletservice.enums.WalletStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletFilterRequest {

    private Long userId;
    private WalletStatus status;
    private String currency;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String sortDirection = "desc";
}