package com.ledgerx.common.dto.request;

import com.ledgerx.common.domain.enums.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ProviderSettlementItemDto {

    @NotBlank(message = "providerTransactionId is required")
    private String providerTransactionId;

    private String internalTransactionId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be positive")
    private BigDecimal amount;

    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    private BigDecimal netAmount;

    @NotNull(message = "currency is required")
    private Currency currency;

    @NotBlank(message = "status is required")
    private String status;

    @Builder.Default
    private Instant settlementTimestamp = Instant.now();
}
