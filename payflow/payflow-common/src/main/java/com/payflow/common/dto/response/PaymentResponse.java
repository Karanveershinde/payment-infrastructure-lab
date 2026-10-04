package com.payflow.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentMethodType;
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
public class PaymentResponse {

    private String paymentId;
    private String merchantId;
    private String idempotencyKey;
    private BigDecimal amount;
    private Currency currency;
    private PaymentStatus status;
    private PaymentMethodType paymentMethodType;
    private ProviderType provider;
    private String providerTransactionId;
    private String customerEmail;
    private String failureCode;
    private String failureMessage;
    private BigDecimal refundedAmount;
    private Map<String, Object> metadata;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
}
