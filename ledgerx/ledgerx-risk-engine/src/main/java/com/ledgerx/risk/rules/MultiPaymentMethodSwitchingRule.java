package com.ledgerx.risk.rules;

import com.ledgerx.common.domain.enums.RiskRuleType;
import com.ledgerx.risk.config.RiskEngineProperties;
import com.ledgerx.risk.model.RiskEvaluationContext;
import com.ledgerx.risk.model.RiskRuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MultiPaymentMethodSwitchingRule implements RiskRule {

    private final RiskEngineProperties properties;

    @Override
    public RiskRuleType getRuleType() {
        return RiskRuleType.MULTI_PAYMENT_METHOD_SWITCHING;
    }

    @Override
    public String getRuleName() {
        return "Multi-Payment Method Switching Rule";
    }

    @Override
    public int getWeight() {
        return properties.getMultiPaymentMethodScore();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public RiskRuleResult evaluate(RiskEvaluationContext context) {
        int methods = context.getCustomerDistinctPaymentMethodsLast10m();
        int limit = properties.getMultiPaymentMethodLimit();

        boolean triggered = methods > limit;
        return RiskRuleResult.builder()
                .ruleType(getRuleType())
                .ruleName(getRuleName())
                .triggered(triggered)
                .score(triggered ? getWeight() : 0)
                .reason(triggered
                        ? String.format("Customer attempted transactions using %d different payment methods in 10 minutes (limit: %d)", methods, limit)
                        : "Payment method usage pattern is normal")
                .build();
    }
}
