package com.payflow.payment.exception;

import com.payflow.common.dto.response.ApiErrorResponse;
import com.payflow.common.exception.IdempotencyConflictException;
import com.payflow.common.exception.IdempotencyPayloadMismatchException;
import com.payflow.common.exception.InvalidStateTransitionException;
import com.payflow.common.exception.PaymentValidationException;
import com.payflow.common.exception.ProviderUnavailableException;
import com.payflow.common.exception.RateLimitExceededException;
import com.payflow.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStateTransition(InvalidStateTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), request,
                Map.of("fromStatus", ex.getFromStatus(), "toStatus", ex.getToStatus()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        log.warn("Idempotency conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request,
                Map.of("idempotencyKey", ex.getIdempotencyKey()));
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyPayloadMismatch(IdempotencyPayloadMismatchException ex, HttpServletRequest request) {
        log.warn("Idempotency payload mismatch: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request,
                Map.of("idempotencyKey", ex.getIdempotencyKey()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getErrorCode(), ex.getMessage(), request,
                Map.of("retryAfterSeconds", ex.getRetryAfterSeconds()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentValidation(PaymentValidationException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderUnavailable(ProviderUnavailableException ex, HttpServletRequest request) {
        log.error("Provider unavailable: {}", ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getErrorCode(), ex.getMessage(), request,
                Map.of("provider", ex.getProviderName()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict during concurrent update: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION_CONFLICT",
                "Transaction conflicted with a concurrent update. Please retry.", request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request payload validation failed", request, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred. Please contact support.", request, null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String code, String message, HttpServletRequest request, Map<String, Object> details) {
        String requestId = (String) request.getAttribute("requestId");
        ApiErrorResponse body = ApiErrorResponse.builder()
                .code(code)
                .message(message)
                .requestId(requestId)
                .timestamp(Instant.now())
                .details(details)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
