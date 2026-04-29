package com.gatepay.paymentservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
    @ExceptionHandler(PaymentServiceException.class)
    public ResponseEntity<ErrorResponse> handleBusinessErrors(
            PaymentServiceException ex, HttpServletRequest request) {

        log.warn("Business error occurred: {}", ex.getMessage());

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

        ex.getBindingResult().getFieldErrors().forEach(err -> {
            errors.put(err.getField(), err.getDefaultMessage());
        });

        log.warn("Validation failed: {}", errors);

        return buildError(
                ErrorCode.VALIDATION_ERROR,
                errors,
                request.getRequestURI()
        );
    }

    // ============================ MISSING REQUEST PARAMS =========================
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing request parameter: {}", ex.getParameterName());

        return buildError(
                ErrorCode.VALIDATION_ERROR,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request.getRequestURI()
        );
    }

    // ============================ TYPE MISMATCH ==================================
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.warn("Type mismatch: parameter '{}' value '{}' is not valid",
                ex.getName(), ex.getValue());

        return buildError(
                ErrorCode.VALIDATION_ERROR,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'",
                request.getRequestURI()
        );
    }

    // ============================ FALLBACK =======================================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error", ex);

        return buildError(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request.getRequestURI()
        );
    }
}