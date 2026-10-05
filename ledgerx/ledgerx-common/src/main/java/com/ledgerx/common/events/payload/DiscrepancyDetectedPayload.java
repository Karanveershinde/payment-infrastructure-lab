package com.ledgerx.common.events.payload;

import com.ledgerx.common.domain.enums.DiscrepancyType;
import com.ledgerx.common.domain.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyDetectedPayload {
    private String discrepancyId;
    private String batchId;
    private String transactionId;
    private String providerTransactionId;
    private ProviderType provider;
    private DiscrepancyType discrepancyType;
    private BigDecimal internalAmount;
    private BigDecimal providerAmount;
    private String internalStatus;
    private String providerStatus;
}
