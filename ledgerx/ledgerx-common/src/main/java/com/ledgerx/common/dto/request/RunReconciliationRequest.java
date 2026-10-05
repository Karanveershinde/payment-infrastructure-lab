package com.ledgerx.common.dto.request;

import com.ledgerx.common.domain.enums.ProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunReconciliationRequest {

    @NotNull(message = "provider is required")
    private ProviderType provider;

    @Builder.Default
    private LocalDate batchDate = LocalDate.now();

    @NotEmpty(message = "settlementRecords cannot be empty")
    @Valid
    private List<ProviderSettlementItemDto> settlementRecords;
}
