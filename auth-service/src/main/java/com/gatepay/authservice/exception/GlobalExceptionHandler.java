package com.gatepay.authservice.exception;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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
    @ExceptionHandler(AuthServiceException.class)
    public ResponseEntity<ErrorResponse> handleBusinessErrors(
            AuthServiceException ex, HttpServletRequest request) {

        log.warn("Business error occurred: {}", ex.getMessage());

        return buildError(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    // ============================ FEIGN EXCEPTIONS ==============================
    @ExceptionHandler(FeignException.NotFound.class)
    public ResponseEntity<ErrorResponse> handleFeignNotFound(
            FeignException.NotFound ex, HttpServletRequest request) {

        log.warn("Downstream service returned 404: {}", ex.getMessage());

        return buildError(
                ErrorCode.USER_NOT_FOUND,
                ErrorCode.USER_NOT_FOUND.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(
            FeignException ex, HttpServletRequest request) {

        log.error("Downstream service error: status={}", ex.status());

        return buildError(
                ErrorCode.USER_SERVICE_UNAVAILABLE,
                ErrorCode.USER_SERVICE_UNAVAILABLE.getMessage(),
                request.getRequestURI()
        );
    }

    // ============================ BAD CREDENTIALS ===============================
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        log.warn("Bad credentials attempt: {}", ex.getMessage());

        return buildError(
                ErrorCode.BAD_CREDENTIALS,
                ErrorCode.BAD_CREDENTIALS.getMessage(),
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

    // ============================ MISSING PARAMS =================================
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing required parameter: {}", ex.getParameterName());

        return buildError(
                ErrorCode.VALIDATION_ERROR,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request.getRequestURI()
        );
    }
}
