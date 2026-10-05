package com.ledgerx.risk.model;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class RiskEvaluationContext {
    private String transactionId;
    private String merchantId;
    private String customerId;
    private ProviderType provider;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Currency currency;
    private String paymentMethodToken;
    private Instant timestamp;
    private Map<String, Object> metadata;

    // Aggregated velocity metrics supplied by velocity tracker
    private int customerTxCountLast60s;
    private int customerTxCountLast10m;
    private int customerFailuresLast10m;
    private int customerDistinctPaymentMethodsLast10m;
    private BigDecimal merchantVolumeLast1h;
    private double merchantRefundRatio;
}
