package com.ledgerx.risk.service;

import com.ledgerx.common.domain.enums.RiskDecision;
import com.ledgerx.common.domain.enums.RiskLevel;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.RiskEvaluationResponse;
import com.ledgerx.risk.config.RiskEngineProperties;
import com.ledgerx.risk.model.RiskEvaluationContext;
import com.ledgerx.risk.model.RiskRuleResult;
import com.ledgerx.risk.rules.RiskRule;
import com.ledgerx.risk.tracker.VelocityTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEngineService {

    private final List<RiskRule> rules;
    private final VelocityTracker velocityTracker;
    private final RiskEngineProperties properties;

    /**
     * Evaluates a transaction request deterministically against all configured risk rules.
     */
    public RiskEvaluationResponse evaluate(IngestTransactionRequest request) {
        Instant startTime = Instant.now();
        String txId = request.getTransactionId() != null ? request.getTransactionId() : UUID.randomUUID().toString();

        log.info("Starting risk evaluation for transaction={} customer={} merchant={} amount={} {}",
                txId, request.getCustomerId(), request.getMerchantId(), request.getAmount(), request.getCurrency());

        // 1. Build context with historical velocity data
        RiskEvaluationContext context = RiskEvaluationContext.builder()
                .transactionId(txId)
                .merchantId(request.getMerchantId())
                .customerId(request.getCustomerId())
                .provider(request.getProvider())
                .transactionType(request.getTransactionType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethodToken(request.getPaymentMethodToken())
                .timestamp(Instant.now())
                .metadata(request.getMetadata())
                .customerTxCountLast60s(velocityTracker.getCustomerTxCountLast60s(request.getCustomerId()))
                .customerTxCountLast10m(velocityTracker.getCustomerTxCountLast10m(request.getCustomerId()))
                .customerFailuresLast10m(velocityTracker.getCustomerFailuresLast10m(request.getCustomerId()))
                .customerDistinctPaymentMethodsLast10m(velocityTracker.getCustomerDistinctPaymentMethodsLast10m(request.getCustomerId()))
                .merchantVolumeLast1h(velocityTracker.getMerchantVolumeLast1h(request.getMerchantId()))
                .merchantRefundRatio(velocityTracker.getMerchantRefundRatio(request.getMerchantId()))
                .build();

        // 2. Evaluate each rule
        List<RiskRuleResult> results = new ArrayList<>();
        int totalScore = 0;
        List<String> triggeredRuleNames = new ArrayList<>();

        for (RiskRule rule : rules) {
            if (rule.isEnabled()) {
                RiskRuleResult res = rule.evaluate(context);
                results.add(res);
                if (res.isTriggered()) {
                    totalScore += res.getScore();
                    triggeredRuleNames.add(res.getRuleName() + " (" + res.getReason() + ")");
                    log.warn("Risk Rule Triggered for tx {}: {} (+{} pts) - {}",
                            txId, res.getRuleName(), res.getScore(), res.getReason());
                }
            }
        }

        // 3. Determine Level and Decision
        RiskLevel level;
        RiskDecision decision;

        if (totalScore >= properties.getBlockThreshold()) {
            level = RiskLevel.BLOCK;
            decision = RiskDecision.REJECT;
        } else if (totalScore >= properties.getHighThreshold()) {
            level = RiskLevel.HIGH;
            decision = RiskDecision.REVIEW;
        } else if (totalScore >= properties.getMediumThreshold()) {
            level = RiskLevel.MEDIUM;
            decision = RiskDecision.REVIEW;
        } else {
            level = RiskLevel.LOW;
            decision = RiskDecision.ACCEPT;
        }

        long latencyMs = Duration.between(startTime, Instant.now()).toMillis();

        // 4. Update velocity state
        boolean isRefund = request.getTransactionType() == TransactionType.REFUND;
        boolean isSuccess = decision != RiskDecision.REJECT;
        velocityTracker.recordTransaction(
                request.getCustomerId(),
                request.getMerchantId(),
                request.getPaymentMethodToken(),
                request.getAmount(),
                isRefund,
                isSuccess
        );

        log.info("Risk evaluation complete for tx {}: score={} level={} decision={} latency={}ms",
                txId, totalScore, level, decision, latencyMs);

        return RiskEvaluationResponse.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(txId)
                .merchantId(request.getMerchantId())
                .customerId(request.getCustomerId())
                .score(totalScore)
                .riskLevel(level)
                .decision(decision)
                .triggeredRules(triggeredRuleNames)
                .evaluationLatencyMs(latencyMs)
                .createdAt(Instant.now())
                .build();
    }
}
