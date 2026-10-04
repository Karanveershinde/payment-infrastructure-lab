package com.payflow.common.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapturePaymentRequest {

    @DecimalMin(value = "0.01", message = "Capture amount must be at least 0.01")
    private BigDecimal amount;

    private String reason;
}
