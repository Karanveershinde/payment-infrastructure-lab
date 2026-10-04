package com.payflow.payment.controller;

import com.payflow.common.dto.request.CapturePaymentRequest;
import com.payflow.common.dto.request.CreatePaymentRequest;
import com.payflow.common.dto.request.RefundPaymentRequest;
import com.payflow.common.dto.response.CaptureResponse;
import com.payflow.common.dto.response.PaymentEventResponse;
import com.payflow.common.dto.response.PaymentResponse;
import com.payflow.common.dto.response.RefundResponse;
import com.payflow.common.exception.PaymentValidationException;
import com.payflow.common.util.JsonUtil;
import com.payflow.payment.security.MerchantPrincipal;
import com.payflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Orchestration API", description = "Endpoints for creating, capturing, refunding, and tracking payment orders")
@SecurityRequirement(name = "ApiKeyAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(
            summary = "Create and process payment order",
            description = "Accepts payment order, enforces idempotency, selects provider, and executes state machine transition."
    )
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(in = ParameterIn.HEADER, required = true, description = "Unique idempotency key per transaction attempt")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal MerchantPrincipal merchant,
            HttpServletRequest httpRequest
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PaymentValidationException("Header 'Idempotency-Key' is required");
        }

        String correlationId = (String) httpRequest.getAttribute("correlationId");
        String rawJson = JsonUtil.toJson(request);

        log.info("Received payment creation request for merchant [{}] idempotencyKey [{}]", merchant.getMerchantId(), idempotencyKey);

        PaymentResponse response = paymentService.createPayment(
                merchant.getMerchantId(),
                idempotencyKey,
                request,
                rawJson,
                correlationId
        );

        HttpStatus status = (response.getStatus().name().equals("FAILED")) ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment status by ID")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable("paymentId") String paymentId,
            @AuthenticationPrincipal MerchantPrincipal merchant
    ) {
        PaymentResponse response = paymentService.getPayment(merchant.getMerchantId(), paymentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/capture")
    @Operation(summary = "Capture an authorized payment")
    public ResponseEntity<CaptureResponse> capturePayment(
            @PathVariable("paymentId") String paymentId,
            @RequestBody(required = false) CapturePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal MerchantPrincipal merchant,
            HttpServletRequest httpRequest
    ) {
        String correlationId = (String) httpRequest.getAttribute("correlationId");
        String effectiveIdempKey = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();

        CaptureResponse response = paymentService.capturePayment(
                merchant.getMerchantId(),
                paymentId,
                request,
                effectiveIdempKey,
                correlationId
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a captured payment")
    public ResponseEntity<RefundResponse> refundPayment(
            @PathVariable("paymentId") String paymentId,
            @Valid @RequestBody RefundPaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal MerchantPrincipal merchant,
            HttpServletRequest httpRequest
    ) {
        String correlationId = (String) httpRequest.getAttribute("correlationId");
        String effectiveIdempKey = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();

        RefundResponse response = paymentService.refundPayment(
                merchant.getMerchantId(),
                paymentId,
                request,
                effectiveIdempKey,
                correlationId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}/events")
    @Operation(summary = "Get immutable state transition audit history for a payment")
    public ResponseEntity<List<PaymentEventResponse>> getPaymentEvents(
            @PathVariable("paymentId") String paymentId,
            @AuthenticationPrincipal MerchantPrincipal merchant
    ) {
        List<PaymentEventResponse> events = paymentService.getPaymentEvents(merchant.getMerchantId(), paymentId);
        return ResponseEntity.ok(events);
    }
}
