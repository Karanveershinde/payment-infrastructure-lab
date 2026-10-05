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
public class HighRiskMerchantSpikeRule implements RiskRule {

    private final RiskEngineProperties properties;

    @Override
    public RiskRuleType getRuleType() {
        return RiskRuleType.HIGH_RISK_MERCHANT_SPIKE;
    }

    @Override
    public String getRuleName() {
        return "High-Risk Merchant Volume Spike Rule";
    }

    @Override
    public int getWeight() {
        return properties.getHighRiskMerchantScore();
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public RiskRuleResult evaluate(RiskEvaluationContext context) {
        BigDecimal volume = context.getMerchantVolumeLast1h() != null ? context.getMerchantVolumeLast1h() : BigDecimal.ZERO;
        BigDecimal limit = properties.getMerchantHourlyVolumeLimit();

        boolean triggered = volume.compareTo(limit) > 0;
        return RiskRuleResult.builder()
                .ruleType(getRuleType())
                .ruleName(getRuleName())
                .triggered(triggered)
                .score(triggered ? getWeight() : 0)
                .reason(triggered
                        ? String.format("Merchant volume in last hour %s %s exceeds sudden spike limit %s", volume, context.getCurrency(), limit)
                        : "Merchant hourly volume is within normal profile")
                .build();
    }
}
