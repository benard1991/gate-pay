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
public class TransactionFilterRequest {

    private TransactionType type;
    private TransactionSource source;
    private TransactionStatus status;
    private LocalDateTime from;
    private LocalDateTime to;
    private int page = 0;
    private int size = 20;
}