package com.ledgerx.risk.model;

import com.ledgerx.common.domain.enums.RiskRuleType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskRuleResult {
    private RiskRuleType ruleType;
    private String ruleName;
    private boolean triggered;
    private int score;
    private String reason;
}
