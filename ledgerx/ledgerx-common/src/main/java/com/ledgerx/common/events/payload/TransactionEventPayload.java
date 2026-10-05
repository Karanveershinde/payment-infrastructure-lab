package com.ledgerx.common.events.payload;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionStatus;
import com.ledgerx.common.domain.enums.TransactionType;
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
public class TransactionEventPayload {
    private String transactionId;
    private String merchantId;
    private String customerId;
    private ProviderType provider;
    private String providerTransactionId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Currency currency;
    private TransactionStatus status;
    private String paymentMethodToken;
    private Instant clientTimestamp;
}
