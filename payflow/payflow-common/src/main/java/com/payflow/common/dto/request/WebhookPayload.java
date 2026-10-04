package com.payflow.common.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {

    private ProviderType provider;
    private String providerEventId;
    private String eventType;
    private String providerTransactionId;
    private String paymentId;
    private PaymentStatus status;
    private BigDecimal amount;
    private Currency currency;
    private String errorCode;
    private String errorMessage;

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp = Instant.now();
}
