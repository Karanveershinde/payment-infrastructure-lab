package com.payflow.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentEventPayload {

    private String paymentId;
    private String merchantId;
    private String idempotencyKey;
    private BigDecimal amount;
    private Currency currency;
    private PaymentStatus status;
    private PaymentStatus previousStatus;
    private ProviderType provider;
    private String providerTransactionId;
    private String failureCode;
    private String failureMessage;
    private BigDecimal refundAmount;
    private String refundId;
    private Map<String, Object> metadata;
    private Instant timestamp;
}
