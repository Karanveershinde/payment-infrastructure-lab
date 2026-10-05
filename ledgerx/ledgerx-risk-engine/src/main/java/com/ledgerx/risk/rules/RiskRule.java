package com.ledgerx.risk.rules;

import com.ledgerx.common.domain.enums.RiskRuleType;
import com.ledgerx.risk.model.RiskEvaluationContext;
import com.ledgerx.risk.model.RiskRuleResult;

public interface RiskRule {

    RiskRuleType getRuleType();

    String getRuleName();

    int getWeight();

    boolean isEnabled();

    RiskRuleResult evaluate(RiskEvaluationContext context);
}
