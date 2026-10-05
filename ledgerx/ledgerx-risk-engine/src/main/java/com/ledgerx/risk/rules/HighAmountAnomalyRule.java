package com.ledgerx.risk.rules;

import com.ledgerx.common.domain.enums.RiskRuleType;
import com.ledgerx.risk.config.RiskEngineProperties;
import com.ledgerx.risk.model.RiskEvaluationContext;
import com.ledgerx.risk.model.RiskRuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class HighAmountAnomalyRule implements RiskRule {

    private final RiskEngineProperties properties;

    @Override
    public RiskRuleType getRuleType() {
        return RiskRuleType.HIGH_AMOUNT_ANOMALY;
    }

    @Override
    public String getRuleName() {
        return "High Amount Anomaly Rule";
    }

    @Override
    public int getWeight() {
        return properties.getHighAmountScore();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public RiskRuleResult evaluate(RiskEvaluationContext context) {
        BigDecimal amount = context.getAmount() != null ? context.getAmount() : BigDecimal.ZERO;
        BigDecimal threshold = properties.getHighAmountThreshold();

        boolean triggered = amount.compareTo(threshold) > 0;
        return RiskRuleResult.builder()
                .ruleType(getRuleType())
                .ruleName(getRuleName())
                .triggered(triggered)
                .score(triggered ? getWeight() : 0)
                .reason(triggered
                        ? String.format("Transaction amount %s %s exceeds high-risk threshold of %s", amount, context.getCurrency(), threshold)
                        : "Transaction amount within normal threshold")
                .build();
    }
}
