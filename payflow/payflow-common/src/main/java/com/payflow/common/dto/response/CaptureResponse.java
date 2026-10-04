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
public class CaptureResponse {

    private String paymentId;
    private PaymentStatus status;
    private BigDecimal capturedAmount;
    private Currency currency;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant capturedAt;
}
