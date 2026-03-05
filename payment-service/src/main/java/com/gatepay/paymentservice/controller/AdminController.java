package com.gatepay.paymentservice.controller;

import com.gatepay.paymentservice.dto.ApiResponse;
import com.gatepay.paymentservice.dto.PaginationResponse;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/payments/admin")
public class AdminController {

    private final PaymentService paymentService;


    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PaginationResponse<PaymentTransaction>>> fetchAllTransactions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Page<PaymentTransaction> result = paymentService.fetchAllTransactions(page, size);

        PaginationResponse<PaymentTransaction> pagination = new PaginationResponse<>();
        pagination.setPage(result.getNumber());
        pagination.setPageSize(result.getSize());
        pagination.setTotalPages(result.getTotalPages());
        pagination.setTotalElements(result.getTotalElements());
        pagination.setContent(result.getContent());

        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Transactions fetched successfully", pagination));
    }


    @GetMapping("/{email}/transactions")
    public ResponseEntity<ApiResponse<PaginationResponse<PaymentTransaction>>> fetchUserTransactions(
            @PathVariable("email") String email,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Page<PaymentTransaction> result = paymentService.fetchUserTransactions(email, page, size);

        PaginationResponse<PaymentTransaction> pagination = new PaginationResponse<>();
        pagination.setPage(result.getNumber());
        pagination.setPageSize(result.getSize());
        pagination.setTotalPages(result.getTotalPages());
        pagination.setTotalElements(result.getTotalElements());
        pagination.setContent(result.getContent());

        ApiResponse<PaginationResponse<PaymentTransaction>> response = new ApiResponse<>(
                200,
                "User transactions fetched successfully",
                pagination
        );

        return ResponseEntity.ok(response);
    }




    @GetMapping("/{reference}")
    public ResponseEntity<ApiResponse<PaymentTransaction>> fetchByReference(
            @PathVariable("reference") String reference
    ) {
        PaymentTransaction transaction = paymentService.fetchTransactionByReference(reference);

        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Transaction fetched successfully", transaction));
    }



}
