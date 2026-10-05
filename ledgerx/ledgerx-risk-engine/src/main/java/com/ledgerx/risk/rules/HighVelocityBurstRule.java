package com.ledgerx.risk.rules;

import com.ledgerx.common.domain.enums.RiskRuleType;
import com.ledgerx.risk.config.RiskEngineProperties;
import com.ledgerx.risk.model.RiskEvaluationContext;
import com.ledgerx.risk.model.RiskRuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HighVelocityBurstRule implements RiskRule {

    private final RiskEngineProperties properties;

    @Override
    public RiskRuleType getRuleType() {
        return RiskRuleType.HIGH_VELOCITY_BURST;
    }

    @Override
    public String getRuleName() {
        return "High Velocity Burst Rule";
    }

    @Override
    public int getWeight() {
        return properties.getVelocityBurstScore();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public RiskRuleResult evaluate(RiskEvaluationContext context) {
        int count = context.getCustomerTxCountLast60s();
        int limit = properties.getVelocityBurstLimit();

        boolean triggered = count >= limit;
        return RiskRuleResult.builder()
                .ruleType(getRuleType())
                .ruleName(getRuleName())
                .triggered(triggered)
                .score(triggered ? getWeight() : 0)
                .reason(triggered
                        ? String.format("Customer initiated %d transactions within 60 seconds (limit: %d)", count, limit)
                        : "Customer transaction velocity within normal bounds")
                .build();
    }
}
