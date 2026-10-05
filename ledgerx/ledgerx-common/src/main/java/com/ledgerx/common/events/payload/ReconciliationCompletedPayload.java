package com.ledgerx.common.events.payload;

import com.ledgerx.common.domain.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationCompletedPayload {
    private String batchId;
    private ProviderType provider;
    private LocalDate batchDate;
    private int totalRecords;
    private int matchedRecords;
    private int discrepantRecords;
    private long durationMs;
}
