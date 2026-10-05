package com.ledgerx.common.dto.request;

import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.common.domain.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestTransactionRequest {

    private String eventId;

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotNull(message = "provider is required")
    private ProviderType provider;

    private String providerTransactionId;

    @NotNull(message = "transactionType is required")
    private TransactionType transactionType;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "currency is required")
    private Currency currency;

    private String paymentMethodToken;

    private Map<String, Object> metadata;
}
