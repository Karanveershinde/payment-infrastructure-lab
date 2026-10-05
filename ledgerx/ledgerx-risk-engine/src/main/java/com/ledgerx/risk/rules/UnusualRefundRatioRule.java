package com.ledgerx.risk.rules;

import com.ledgerx.common.domain.enums.RiskRuleType;
import com.ledgerx.risk.config.RiskEngineProperties;
import com.ledgerx.risk.model.RiskEvaluationContext;
import com.ledgerx.risk.model.RiskRuleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnusualRefundRatioRule implements RiskRule {

    private final RiskEngineProperties properties;

    @Override
    public RiskRuleType getRuleType() {
        return RiskRuleType.UNUSUAL_REFUND_RATIO;
    }

    @Override
    public String getRuleName() {
        return "Unusual Refund Ratio Rule";
    }

    @Override
    public int getWeight() {
        return properties.getUnusualRefundRatioScore();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public RiskRuleResult evaluate(RiskEvaluationContext context) {
        double refundRatio = context.getMerchantRefundRatio();
        double limit = properties.getRefundRatioLimit();

        boolean triggered = refundRatio > limit;
        return RiskRuleResult.builder()
                .ruleType(getRuleType())
                .ruleName(getRuleName())
                .triggered(triggered)
                .score(triggered ? getWeight() : 0)
                .reason(triggered
                        ? String.format("Merchant refund ratio %.2f%% exceeds maximum threshold %.2f%%", refundRatio * 100, limit * 100)
                        : "Merchant refund ratio is within acceptable parameters")
                .build();
    }
}
