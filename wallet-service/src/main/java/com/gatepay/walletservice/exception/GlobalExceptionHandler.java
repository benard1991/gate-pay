package com.gatepay.walletservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ErrorResponse> buildError(ErrorCode code, Object message, String path) {
        return ResponseEntity
                .status(code.getStatus())
                .body(
                        ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(code.getStatus().value())
                                .error(code.getStatus().getReasonPhrase())
                                .message(message)
                                .path(path)
                                .code(code.getCode())
                                .build()
                );
    }

    // ============================ BUSINESS EXCEPTIONS ============================
    @ExceptionHandler(WalletServiceException.class)
    public ResponseEntity<ErrorResponse> handleBusinessErrors(
            WalletServiceException ex, HttpServletRequest request) {

        log.warn("Wallet business error: code={} message={}",
                ex.getErrorCode().getCode(), ex.getMessage());

        return buildError(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    // ============================ VALIDATION ERRORS =============================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage())
        );

        log.warn("Validation failed: {}", errors);

        return buildError(
                ErrorCode.VALIDATION_ERROR,
                errors,
                request.getRequestURI()
        );
    }

    // ============================ METHOD NOT ALLOWED ============================
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("Method not allowed: {}", ex.getMessage());

        return buildError(
                ErrorCode.METHOD_NOT_ALLOWED,
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    // ============================ FALLBACK =======================================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return buildError(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request.getRequestURI()
        );
    }
}