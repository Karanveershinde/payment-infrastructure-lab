package com.ledgerx.common.dto.response;

import com.ledgerx.common.domain.enums.DiscrepancyResolutionStatus;
import com.ledgerx.common.domain.enums.DiscrepancyType;
import com.ledgerx.common.domain.enums.ProviderType;
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
public class DiscrepancyResponse {
    private String id;
    private String batchId;
    private String transactionId;
    private String providerTransactionId;
    private ProviderType provider;
    private DiscrepancyType discrepancyType;
    private BigDecimal internalAmount;
    private BigDecimal providerAmount;
    private String internalStatus;
    private String providerStatus;
    private DiscrepancyResolutionStatus resolutionStatus;
    private String resolutionNotes;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
}
