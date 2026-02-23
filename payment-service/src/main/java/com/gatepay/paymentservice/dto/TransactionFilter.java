package com.gatepay.paymentservice.dto;

import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.model.enums.TransactionType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class TransactionFilter {

    private TransactionStatus status;

    private TransactionType transactionType;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)  // yyyy-MM-dd
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)  // yyyy-MM-dd
    private LocalDate endDate;
}
