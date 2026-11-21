package com.gatepay.paymentservice.controller;

import com.gatepay.paymentservice.dto.*;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.model.enums.TransactionType;
import com.gatepay.paymentservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.gatepay.paymentservice.security.CallerIdentity;
import com.gatepay.paymentservice.exception.ErrorCode;
import com.gatepay.paymentservice.exception.PaymentServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
        // you can only start a payment for your own account
        CallerIdentity.requireSelf(request.getUserId());

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
    public ResponseEntity<VerifyPaymentResponse> verifyPayment(
            @RequestBody @Valid VerifyPaymentRequest request,
            HttpServletRequest httpRequest) {

        String performedBy = "SYSTEM";
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        VerifyPaymentResponse response = paymentService.verifyPayment(
                request, performedBy, ipAddress, userAgent);

        if (response == null) {
            return ResponseEntity.internalServerError().build();
        }

        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<ApiResponse<PaginationResponse<PaymentTransaction>>> fetchUserTransactions(
            @PathVariable("userId") String userId,
            @RequestParam(name = "status", required = false) TransactionStatus status,
            @RequestParam(name = "transactionType", required = false) TransactionType transactionType,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        // userId is a String in this API; anything that isn't the caller's own id is refused
        if (!String.valueOf(CallerIdentity.currentUserId()).equals(userId)) {
            throw new PaymentServiceException(ErrorCode.ACCESS_DENIED, "You can only access your own account");
        }

        TransactionFilter filter = new TransactionFilter();
        filter.setStatus(status);
        filter.setTransactionType(transactionType);
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);

        Page<PaymentTransaction> result = paymentService.fetchUserTransactionsByUserId(
                userId, filter, page, size
        );

        PaginationResponse<PaymentTransaction> pagination = new PaginationResponse<>();
        pagination.setPage(result.getNumber());
        pagination.setPageSize(result.getSize());
        pagination.setTotalPages(result.getTotalPages());
        pagination.setTotalElements(result.getTotalElements());
        pagination.setContent(result.getContent());

        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(),
                        "User transactions fetched successfully",
                        pagination)
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader == null ? request.getRemoteAddr() : xfHeader.split(",")[0];
    }
}
