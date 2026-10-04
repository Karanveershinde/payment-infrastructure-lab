package com.payflow.payment.service;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.StateTransitionTrigger;
import com.payflow.common.domain.statemachine.PaymentStateMachine;
import com.payflow.common.events.PaymentEvent;
import com.payflow.common.events.PaymentEventPayload;
import com.payflow.common.events.PaymentEventTopics;
import com.payflow.payment.domain.entity.PaymentEntity;
import com.payflow.payment.domain.entity.PaymentStateTransitionEntity;
import com.payflow.payment.event.KafkaPaymentEventPublisher;
import com.payflow.payment.observability.MetricsService;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.PaymentStateTransitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StateMachineService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final KafkaPaymentEventPublisher eventPublisher;
    private final MetricsService metricsService;

    @Transactional(propagation = Propagation.REQUIRED)
    public PaymentEntity transitionState(
            PaymentEntity payment,
            PaymentStatus targetStatus,
            StateTransitionTrigger trigger,
            String reason,
            String correlationId
    ) {
        PaymentStatus currentStatus = payment.getStatus();

        if (currentStatus == targetStatus) {
            log.info("Idempotent state transition request for payment [{}]: already in state [{}]",
                    payment.getId(), targetStatus);
            return payment;
        }

        // 1. Strict state machine validation
        PaymentStateMachine.validateTransition(currentStatus, targetStatus);

        log.info("Transitioning payment [{}] from [{}] -> [{}] via trigger [{}] reason: {}",
                payment.getId(), currentStatus, targetStatus, trigger, reason);

        // 2. Record immutable state transition audit history
        PaymentStateTransitionEntity transition = PaymentStateTransitionEntity.builder()
                .id(UUID.randomUUID().toString())
                .paymentId(payment.getId())
                .fromState(currentStatus)
                .toState(targetStatus)
                .triggerSource(trigger)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
        transitionRepository.save(transition);

        // 3. Update Payment entity state
        payment.setStatus(targetStatus);
        payment.setUpdatedAt(Instant.now());
        PaymentEntity updatedPayment = paymentRepository.save(payment);

        // 4. Publish corresponding Kafka lifecycle event
        publishLifecycleEvent(updatedPayment, currentStatus, targetStatus, correlationId);

        return updatedPayment;
    }

    private void publishLifecycleEvent(PaymentEntity payment, PaymentStatus fromState, PaymentStatus toState, String correlationId) {
        String topic = switch (toState) {
            case CREATED -> PaymentEventTopics.PAYMENT_CREATED;
            case PROCESSING -> PaymentEventTopics.PAYMENT_PROCESSING;
            case AUTHORIZED -> PaymentEventTopics.PAYMENT_AUTHORIZED;
            case CAPTURED -> PaymentEventTopics.PAYMENT_SUCCESS;
            case FAILED -> PaymentEventTopics.PAYMENT_FAILED;
            case CANCELLED -> PaymentEventTopics.PAYMENT_CANCELLED;
            case REFUND_PENDING -> PaymentEventTopics.PAYMENT_REFUND_REQUESTED;
            case REFUNDED -> PaymentEventTopics.PAYMENT_REFUNDED;
        };

        PaymentEventPayload payload = PaymentEventPayload.builder()
                .paymentId(payment.getId())
                .merchantId(payment.getMerchantId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(toState)
                .previousStatus(fromState)
                .provider(payment.getProvider())
                .providerTransactionId(payment.getProviderTransactionId())
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .refundAmount(payment.getRefundedAmount())
                .timestamp(Instant.now())
                .build();

        PaymentEvent<PaymentEventPayload> event = PaymentEvent.of(
                topic,
                payment.getId(),
                payment.getMerchantId(),
                correlationId,
                payload
        );

        eventPublisher.publishEvent(topic, event);

        // Record metrics
        if (toState == PaymentStatus.CAPTURED) {
            metricsService.incrementPaymentSuccess(payment.getMerchantId(), payment.getProvider() != null ? payment.getProvider().name() : "NONE");
        } else if (toState == PaymentStatus.FAILED) {
            metricsService.incrementPaymentFailure(payment.getMerchantId(), payment.getProvider() != null ? payment.getProvider().name() : "NONE", payment.getFailureCode());
        }
    }
}
