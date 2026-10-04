package com.payflow.common.dto.request;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentMethodType;
import com.payflow.common.domain.enums.ProviderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
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
public class CreatePaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.50", message = "Minimum transaction amount is 0.50")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private Currency currency;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType paymentMethodType;

    @NotBlank(message = "Payment method token is required")
    private String paymentMethodToken;

    @Email(message = "Invalid customer email format")
    private String customerEmail;

    @Builder.Default
    private String captureMethod = "AUTOMATIC";

    private ProviderType preferredProvider;

    private String description;

    private Map<String, Object> metadata;
}
