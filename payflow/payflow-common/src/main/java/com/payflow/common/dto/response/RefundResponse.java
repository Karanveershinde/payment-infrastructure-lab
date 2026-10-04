package com.payflow.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
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
public class RefundResponse {

    private String refundId;
    private String paymentId;
    private BigDecimal amount;
    private Currency currency;
    private PaymentStatus paymentStatus;
    private String providerRefundId;
    private String reason;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
