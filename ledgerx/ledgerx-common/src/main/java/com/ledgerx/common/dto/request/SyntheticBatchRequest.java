package com.ledgerx.common.dto.request;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyntheticBatchRequest {

    @Builder.Default
    @Min(1)
    @Max(50000)
    private int recordCount = 1000;

    @Builder.Default
    private ProviderType provider = ProviderType.PROVIDER_A;

    @Builder.Default
    private Currency currency = Currency.USD;

    @Builder.Default
    @Min(0)
    @Max(100)
    private double duplicateRate = 0.02; // 2% duplicates

    @Builder.Default
    @Min(0)
    @Max(100)
    private double missingInternalRate = 0.01; // 1% missing from internal system

    @Builder.Default
    @Min(0)
    @Max(100)
    private double missingProviderRate = 0.01; // 1% missing from provider report

    @Builder.Default
    @Min(0)
    @Max(100)
    private double amountMismatchRate = 0.02; // 2% amount discrepancies

    @Builder.Default
    @Min(0)
    @Max(100)
    private double statusMismatchRate = 0.02; // 2% status discrepancies
}
