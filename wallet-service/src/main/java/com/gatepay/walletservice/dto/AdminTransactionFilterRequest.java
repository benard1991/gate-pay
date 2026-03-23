package com.gatepay.walletservice.dto;

import com.gatepay.walletservice.enums.TransactionSource;
import com.gatepay.walletservice.enums.TransactionStatus;
import com.gatepay.walletservice.enums.TransactionType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminTransactionFilterRequest {

    private Long walletId;
    private Long userId;
    private TransactionType type;
    private TransactionSource source;
    private TransactionStatus status;
    private LocalDateTime from;
    private LocalDateTime to;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String sortDirection = "desc";
}