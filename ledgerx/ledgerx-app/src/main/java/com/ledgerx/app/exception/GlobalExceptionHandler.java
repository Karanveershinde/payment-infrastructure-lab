package com.ledgerx.app.exception;

import com.ledgerx.app.metrics.MetricsService;
import com.ledgerx.common.dto.response.ApiErrorResponse;
import com.ledgerx.common.exception.AccountInactiveException;
import com.ledgerx.common.exception.AccountNotFoundException;
import com.ledgerx.common.exception.DiscrepancyNotFoundException;
import com.ledgerx.common.exception.DuplicateEventException;
import com.ledgerx.common.exception.LedgerInvariantException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MetricsService metricsService;

    @ExceptionHandler(LedgerInvariantException.class)
    public ResponseEntity<ApiErrorResponse> handleLedgerInvariant(LedgerInvariantException ex, HttpServletRequest request) {
        log.error("Ledger Invariant Violation: {}", ex.getMessage());
        metricsService.incrementLedgerInvariantFailures();
        metricsService.incrementEventsFailed();

        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("LEDGER_INVARIANT_VIOLATION")
                .message(ex.getMessage())
                .requestId(extractRequestId(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        log.warn("Account Not Found: {}", ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("ACCOUNT_NOT_FOUND")
                .message(ex.getMessage())
                .requestId(extractRequestId(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DiscrepancyNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDiscrepancyNotFound(DiscrepancyNotFoundException ex, HttpServletRequest request) {
        log.warn("Discrepancy Not Found: {}", ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("DISCREPANCY_NOT_FOUND")
                .message(ex.getMessage())
                .requestId(extractRequestId(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEvent(DuplicateEventException ex, HttpServletRequest request) {
        log.warn("Duplicate Event: {}", ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("DUPLICATE_EVENT")
                .message(ex.getMessage())
                .requestId(extractRequestId(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountInactive(AccountInactiveException ex, HttpServletRequest request) {
        log.warn("Account Inactive: {}", ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("ACCOUNT_INACTIVE")
                .message(ex.getMessage())
                .requestId(extractRequestId(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }

        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .requestId(extractRequestId(request))
                .details(details)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid Argument: {}", ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("INVALID_ARGUMENT")
                .message(ex.getMessage())
                .requestId(extractRequestId(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled Exception: ", ex);
        metricsService.incrementEventsFailed();

        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please contact system support.")
                .requestId(extractRequestId(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String extractRequestId(HttpServletRequest request) {
        String reqId = request.getHeader("X-Request-Id");
        return reqId != null && !reqId.isBlank() ? reqId : UUID.randomUUID().toString();
    }
}
