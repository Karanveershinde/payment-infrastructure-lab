package com.ledgerx.common.dto.response;

import com.ledgerx.common.domain.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationBatchResponse {
    private String id;
    private ProviderType provider;
    private LocalDate batchDate;
    private int totalRecords;
    private int matchedRecords;
    private int discrepantRecords;
    private BigDecimal totalInternalAmount;
    private BigDecimal totalProviderAmount;
    private String status;
    private Long executionDurationMs;
    private Instant createdAt;
    private Instant completedAt;
}
