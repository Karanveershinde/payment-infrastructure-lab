package com.ledgerx.risk.rules;

import com.ledgerx.common.domain.enums.RiskRuleType;
import com.ledgerx.risk.config.RiskEngineProperties;
import com.ledgerx.risk.model.RiskEvaluationContext;
import com.ledgerx.risk.model.RiskRuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepeatFailureSurgeRule implements RiskRule {

    private final RiskEngineProperties properties;

    @Override
    public RiskRuleType getRuleType() {
        return RiskRuleType.REPEAT_FAILURE_SURGE;
    }

    @Override
    public String getRuleName() {
        return "Repeat Failure Surge Rule";
    }

    @Override
    public int getWeight() {
        return properties.getRepeatFailureScore();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public RiskRuleResult evaluate(RiskEvaluationContext context) {
        int failures = context.getCustomerFailuresLast10m();
        int limit = properties.getRepeatFailureLimit();

        boolean triggered = failures >= limit;
        return RiskRuleResult.builder()
                .ruleType(getRuleType())
                .ruleName(getRuleName())
                .triggered(triggered)
                .score(triggered ? getWeight() : 0)
                .reason(triggered
                        ? String.format("Customer has %d failed transaction attempts in last 10 minutes (threshold: %d)", failures, limit)
                        : "Customer failure rate within acceptable tolerance")
                .build();
    }
}
