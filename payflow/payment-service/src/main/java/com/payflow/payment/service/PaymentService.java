package com.payflow.payment.service;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.StateTransitionTrigger;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.dto.request.CapturePaymentRequest;
import com.payflow.common.dto.request.CreatePaymentRequest;
import com.payflow.common.dto.request.RefundPaymentRequest;
import com.payflow.common.dto.response.CaptureResponse;
import com.payflow.common.dto.response.PaymentEventResponse;
import com.payflow.common.dto.response.PaymentResponse;
import com.payflow.common.dto.response.RefundResponse;
import com.payflow.common.exception.PaymentValidationException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.JsonUtil;
import com.payflow.payment.domain.entity.PaymentEntity;
import com.payflow.payment.domain.entity.PaymentStateTransitionEntity;
import com.payflow.payment.domain.entity.RefundEntity;
import com.payflow.payment.observability.MetricsService;
import com.payflow.payment.provider.ProviderRouter;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.PaymentStateTransitionRepository;
import com.payflow.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final RefundRepository refundRepository;
    private final StateMachineService stateMachineService;
    private final ProviderRouter providerRouter;
    private final IdempotencyService idempotencyService;
    private final MetricsService metricsService;

    public PaymentResponse createPayment(
            String merchantId,
            String idempotencyKey,
            CreatePaymentRequest request,
            String rawJsonPayload,
            String correlationId
    ) {
        long startTime = System.currentTimeMillis();
        metricsService.incrementPaymentRequests(merchantId, request.getPaymentMethodType().name());

        // 1. Check idempotency reservation
        IdempotencyService.IdempotencyRecord idempRecord = idempotencyService.checkAndReserve(merchantId, idempotencyKey, rawJsonPayload);
        if (idempRecord.isReplay()) {
            return JsonUtil.fromJson(idempRecord.getResponseBody(), PaymentResponse.class);
        }

        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        PaymentEntity payment;

        try {
            // 2. Initialize Payment record in CREATED state
            payment = PaymentEntity.builder()
                    .id(paymentId)
                    .merchantId(merchantId)
                    .idempotencyKey(idempotencyKey)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(PaymentStatus.CREATED)
                    .paymentMethodType(request.getPaymentMethodType())
                    .paymentMethodToken(request.getPaymentMethodToken())
                    .customerEmail(request.getCustomerEmail())
                    .metadataJson(JsonUtil.toJson(request.getMetadata()))
                    .refundedAmount(BigDecimal.ZERO)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            payment = paymentRepository.save(payment);

            // 3. Advance state to PROCESSING
            payment = transitionPayment(payment, PaymentStatus.PROCESSING, StateTransitionTrigger.PROVIDER_PROCESSING_STARTED, "Dispatching to provider router", correlationId);

            // 4. Route payment to provider with multi-tier failover and circuit breaking
            ProviderModels.AuthorizeRequest authRequest = ProviderModels.AuthorizeRequest.builder()
                    .paymentId(paymentId)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .paymentMethodToken(request.getPaymentMethodToken())
                    .customerEmail(request.getCustomerEmail())
                    .metadata(request.getMetadata())
                    .build();

            ProviderRouter.RoutingResult routingResult = providerRouter.routeAuthorize(paymentId, request.getPreferredProvider(), authRequest);
            ProviderModels.ProviderResponse providerResp = routingResult.getResponse();

            payment.setProvider(routingResult.getProvider());
            payment.setProviderTransactionId(providerResp.getProviderTransactionId());

            if (providerResp.isSuccess()) {
                if ("MANUAL".equalsIgnoreCase(request.getCaptureMethod())) {
                    payment = transitionPayment(payment, PaymentStatus.AUTHORIZED, StateTransitionTrigger.PROVIDER_AUTH_SUCCESS, "Payment authorized by provider", correlationId);
                } else {
                    payment = transitionPayment(payment, PaymentStatus.AUTHORIZED, StateTransitionTrigger.PROVIDER_AUTH_SUCCESS, "Authorized before auto-capture", correlationId);
                    payment = transitionPayment(payment, PaymentStatus.CAPTURED, StateTransitionTrigger.PROVIDER_CAPTURE_SUCCESS, "Auto-captured settlement", correlationId);
                }
            } else {
                payment.setFailureCode(providerResp.getErrorCode());
                payment.setFailureMessage(providerResp.getErrorMessage());
                payment = transitionPayment(payment, PaymentStatus.FAILED, StateTransitionTrigger.PROVIDER_AUTH_FAILED, providerResp.getErrorMessage(), correlationId);
            }

            PaymentResponse response = mapToResponse(payment);

            // 5. Complete idempotency record
            int statusCode = (payment.getStatus() == PaymentStatus.FAILED) ? 400 : 201;
            idempotencyService.complete(merchantId, idempotencyKey, JsonUtil.toJson(response), statusCode);

            long totalDuration = System.currentTimeMillis() - startTime;
            metricsService.recordPaymentLatency(merchantId, payment.getStatus().name(), totalDuration);

            return response;

        } catch (Exception e) {
            log.error("Fatal error during payment execution for key [{}]: {}", idempotencyKey, e.getMessage(), e);
            idempotencyService.fail(merchantId, idempotencyKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String merchantId, String paymentId) {
        PaymentEntity payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return mapToResponse(payment);
    }

    @Transactional
    public CaptureResponse capturePayment(String merchantId, String paymentId, CapturePaymentRequest request, String idempotencyKey, String correlationId) {
        PaymentEntity payment = paymentRepository.findByIdAndMerchantIdWithLock(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new PaymentValidationException("Payment cannot be captured from state: " + payment.getStatus());
        }

        BigDecimal captureAmount = (request != null && request.getAmount() != null) ? request.getAmount() : payment.getAmount();
        if (captureAmount.compareTo(payment.getAmount()) > 0) {
            throw new PaymentValidationException("Capture amount cannot exceed authorized amount of " + payment.getAmount());
        }

        ProviderModels.CaptureRequest simRequest = ProviderModels.CaptureRequest.builder()
                .paymentId(paymentId)
                .providerTransactionId(payment.getProviderTransactionId())
                .amount(captureAmount)
                .currency(payment.getCurrency())
                .reason(request != null ? request.getReason() : "Merchant manual capture")
                .build();

        ProviderModels.ProviderResponse providerResp = providerRouter.routeCapture(payment.getProvider(), simRequest);

        if (!providerResp.isSuccess()) {
            throw new PaymentValidationException("Provider capture rejected: " + providerResp.getErrorMessage());
        }

        payment = stateMachineService.transitionState(payment, PaymentStatus.CAPTURED, StateTransitionTrigger.PROVIDER_CAPTURE_SUCCESS, "Manual capture settlement", correlationId);

        return CaptureResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .capturedAmount(captureAmount)
                .currency(payment.getCurrency())
                .capturedAt(Instant.now())
                .build();
    }

    @Transactional
    public RefundResponse refundPayment(String merchantId, String paymentId, RefundPaymentRequest request, String idempotencyKey, String correlationId) {
        PaymentEntity payment = paymentRepository.findByIdAndMerchantIdWithLock(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new PaymentValidationException("Only CAPTURED payments can be refunded. Current status: " + payment.getStatus());
        }

        BigDecimal remainingRefundable = payment.getAmount().subtract(payment.getRefundedAmount());
        if (request.getAmount().compareTo(remainingRefundable) > 0) {
            throw new PaymentValidationException(String.format("Refund amount [%s] exceeds remaining refundable balance [%s]",
                    request.getAmount(), remainingRefundable));
        }

        payment = stateMachineService.transitionState(payment, PaymentStatus.REFUND_PENDING, StateTransitionTrigger.REFUND_INITIATED, request.getReason(), correlationId);

        ProviderModels.RefundRequest refundReq = ProviderModels.RefundRequest.builder()
                .paymentId(paymentId)
                .providerTransactionId(payment.getProviderTransactionId())
                .amount(request.getAmount())
                .currency(payment.getCurrency())
                .reason(request.getReason())
                .build();

        ProviderModels.ProviderResponse providerResp = providerRouter.routeRefund(payment.getProvider(), refundReq);

        if (providerResp.isSuccess()) {
            BigDecimal newRefundedAmount = payment.getRefundedAmount().add(request.getAmount());
            payment.setRefundedAmount(newRefundedAmount);
            boolean isFullyRefunded = newRefundedAmount.compareTo(payment.getAmount()) >= 0;
            PaymentStatus targetStatus = isFullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.CAPTURED;
            String reason = isFullyRefunded ? "Refund fully settled by provider" : "Partial refund settled by provider";
            payment = stateMachineService.transitionState(payment, targetStatus, StateTransitionTrigger.REFUND_SUCCESS, reason, correlationId);

            String refundId = "ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            RefundEntity refund = RefundEntity.builder()
                    .id(refundId)
                    .paymentId(payment.getId())
                    .merchantId(merchantId)
                    .amount(request.getAmount())
                    .currency(payment.getCurrency())
                    .reason(request.getReason())
                    .status(PaymentStatus.REFUNDED)
                    .providerRefundId(providerResp.getProviderTransactionId())
                    .createdAt(Instant.now())
                    .build();
            refundRepository.save(refund);

            return RefundResponse.builder()
                    .refundId(refundId)
                    .paymentId(payment.getId())
                    .amount(request.getAmount())
                    .currency(payment.getCurrency())
                    .paymentStatus(refund.getStatus())
                    .providerRefundId(providerResp.getProviderTransactionId())
                    .reason(request.getReason())
                    .createdAt(refund.getCreatedAt())
                    .build();
        } else {
            stateMachineService.transitionState(payment, PaymentStatus.CAPTURED, StateTransitionTrigger.REFUND_FAILED, "Provider rejected refund: " + providerResp.getErrorMessage(), correlationId);
            throw new PaymentValidationException("Provider rejected refund request: " + providerResp.getErrorMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentEventResponse> getPaymentEvents(String merchantId, String paymentId) {
        paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        List<PaymentStateTransitionEntity> transitions = transitionRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
        return transitions.stream()
                .map(t -> PaymentEventResponse.builder()
                        .transitionId(t.getId())
                        .paymentId(t.getPaymentId())
                        .fromState(t.getFromState())
                        .toState(t.getToState())
                        .trigger(t.getTriggerSource())
                        .reason(t.getReason())
                        .createdAt(t.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public PaymentEntity transitionPayment(PaymentEntity payment, PaymentStatus targetStatus, StateTransitionTrigger trigger, String reason, String correlationId) {
        return stateMachineService.transitionState(payment, targetStatus, trigger, reason, correlationId);
    }

    private PaymentResponse mapToResponse(PaymentEntity payment) {
        Map<String, Object> metadata = null;
        if (payment.getMetadataJson() != null) {
            try {
                metadata = JsonUtil.fromJson(payment.getMetadataJson(), Map.class);
            } catch (Exception ignored) {}
        }

        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .merchantId(payment.getMerchantId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethodType(payment.getPaymentMethodType())
                .provider(payment.getProvider())
                .providerTransactionId(payment.getProviderTransactionId())
                .customerEmail(payment.getCustomerEmail())
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .refundedAmount(payment.getRefundedAmount())
                .metadata(metadata)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
