package com.ledgerx.risk.service;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.RiskDecision;
import com.ledgerx.common.domain.enums.RiskLevel;
import com.ledgerx.common.domain.enums.TransactionType;
import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.RiskEvaluationResponse;
import com.ledgerx.risk.config.RiskEngineProperties;
import com.ledgerx.risk.rules.HighAmountAnomalyRule;
import com.ledgerx.risk.rules.HighRiskMerchantSpikeRule;
import com.ledgerx.risk.rules.HighVelocityBurstRule;
import com.ledgerx.risk.rules.MultiPaymentMethodSwitchingRule;
import com.ledgerx.risk.rules.RepeatFailureSurgeRule;
import com.ledgerx.risk.rules.RiskRule;
import com.ledgerx.risk.rules.UnusualRefundRatioRule;
import com.ledgerx.risk.tracker.InMemoryVelocityTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskEngineServiceTest {

    private RiskEngineProperties properties;
    private InMemoryVelocityTracker velocityTracker;
    private RiskEngineService riskEngineService;

    @BeforeEach
    void setUp() {
        properties = new RiskEngineProperties();
        velocityTracker = new InMemoryVelocityTracker();

        List<RiskRule> rules = List.of(
                new HighAmountAnomalyRule(properties),
                new HighVelocityBurstRule(properties),
                new RepeatFailureSurgeRule(properties),
                new MultiPaymentMethodSwitchingRule(properties),
                new HighRiskMerchantSpikeRule(properties),
                new UnusualRefundRatioRule(properties)
        );

        riskEngineService = new RiskEngineService(rules, velocityTracker, properties);
    }

    @Test
    @DisplayName("Should evaluate normal low-risk transaction as LOW and ACCEPT")
    void shouldAcceptLowRiskTransaction() {
        IngestTransactionRequest request = IngestTransactionRequest.builder()
                .transactionId("tx-norm-001")
                .merchantId("merch-100")
                .customerId("cust-200")
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("120.00"))
                .currency(Currency.USD)
                .paymentMethodToken("pm_card_visa")
                .build();

        RiskEvaluationResponse response = riskEngineService.evaluate(request);

        assertThat(response).isNotNull();
        assertThat(response.getScore()).isEqualTo(0);
        assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.getDecision()).isEqualTo(RiskDecision.ACCEPT);
        assertThat(response.getTriggeredRules()).isEmpty();
    }

    @Test
    @DisplayName("Should trigger High Amount Anomaly rule for large transactions")
    void shouldTriggerHighAmountAnomaly() {
        IngestTransactionRequest request = IngestTransactionRequest.builder()
                .transactionId("tx-large-001")
                .merchantId("merch-100")
                .customerId("cust-200")
                .provider(ProviderType.STRIPE)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("7500.00")) // > 5000 threshold
                .currency(Currency.USD)
                .paymentMethodToken("pm_card_visa")
                .build();

        RiskEvaluationResponse response = riskEngineService.evaluate(request);

        assertThat(response.getScore()).isEqualTo(30);
        assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.LOW); // Score 30 is at threshold for LOW
        assertThat(response.getDecision()).isEqualTo(RiskDecision.ACCEPT);
        assertThat(response.getTriggeredRules()).hasSize(1);
        assertThat(response.getTriggeredRules().get(0)).contains("High Amount Anomaly Rule");
    }

    @Test
    @DisplayName("Should block and reject transaction when multiple risk rules accumulate > 80 score")
    void shouldBlockHighRiskBurstTransaction() {
        String customerId = "cust-attacker";
        String merchantId = "merch-target";

        // Seed 5 rapid prior transactions within 60s
        for (int i = 0; i < 5; i++) {
            velocityTracker.recordTransaction(customerId, merchantId, "pm_token_" + i, new BigDecimal("100.00"), false, false);
        }

        // Triggering transaction: High Amount ($6000) + Velocity Burst (6th tx) + Repeat Failures (5 failures) + Multi-Card (>2 cards)
        IngestTransactionRequest request = IngestTransactionRequest.builder()
                .transactionId("tx-attack-001")
                .merchantId(merchantId)
                .customerId(customerId)
                .provider(ProviderType.ADYEN)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("6000.00"))
                .currency(Currency.USD)
                .paymentMethodToken("pm_card_mastercard")
                .build();

        RiskEvaluationResponse response = riskEngineService.evaluate(request);

        // High Amount (30) + Velocity Burst (25) + Repeat Failures (20) + Multi Card (20) = 95
        assertThat(response.getScore()).isGreaterThanOrEqualTo(85);
        assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.BLOCK);
        assertThat(response.getDecision()).isEqualTo(RiskDecision.REJECT);
        assertThat(response.getTriggeredRules()).hasSizeGreaterThanOrEqualTo(3);
    }
}
