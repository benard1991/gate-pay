package com.gatepay.paymentservice.controller;

import com.gatepay.paymentservice.dto.*;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initializePayment")
    public ResponseEntity<PaymentResponse> initializePayment(
            @RequestParam("provider") PaymentProvider provider,
            @RequestBody PaymentRequest request,
            HttpServletRequest httpRequest) {

        PaymentResponse response = paymentService.initializePayment(
                request,
                provider,
                getClientIp(httpRequest)
        );

        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }


    @PostMapping("/verifyPayment")
    public ResponseEntity<VerifyPaymentResponse> verifyPayment(@RequestBody @Valid VerifyPaymentRequest provider, HttpServletRequest request) {
        String performedBy = "SYSTEM";

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        VerifyPaymentResponse response = paymentService.verifyPayment(provider,performedBy, ipAddress, userAgent);

        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

//
//    @GetMapping("/{userId}/transactions")
//    public ResponseEntity<ApiResponse<PaginationResponse<PaymentTransaction>>> fetchUserTransactions(
//            @PathVariable String userId,
//            @ModelAttribute TransactionFilter filter,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size
//    ) {
//
//        LocalDateTime startDateTime = filter.getStartDate() != null
//                ? filter.getStartDate().atStartOfDay()
//                : null;
//
//        LocalDateTime endDateTime = filter.getEndDate() != null
//                ? filter.getEndDate().atTime(23, 59, 59)
//                : null;
//
//        Page<PaymentTransaction> result = paymentService.fetchUserTransactionsByUserId(
//                userId, filter, page, size
//        );
//
//        PaginationResponse<PaymentTransaction> pagination = new PaginationResponse<>();
//        pagination.setPage(result.getNumber());
//        pagination.setPageSize(result.getSize());
//        pagination.setTotalPages(result.getTotalPages());
//        pagination.setTotalElements(result.getTotalElements());
//        pagination.setContent(result.getContent());
//
//        return ResponseEntity.ok(
//                new ApiResponse<>(HttpStatus.OK.value(), "User transactions fetched successfully", pagination)
//        );
//    }






    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0];
    }
}
