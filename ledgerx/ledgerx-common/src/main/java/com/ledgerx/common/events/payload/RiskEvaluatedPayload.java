package com.ledgerx.common.events.payload;

import com.ledgerx.common.domain.enums.RiskDecision;
import com.ledgerx.common.domain.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskEvaluatedPayload {
    private String transactionId;
    private String merchantId;
    private String customerId;
    private int score;
    private RiskLevel riskLevel;
    private RiskDecision decision;
    private List<String> triggeredRules;
}
