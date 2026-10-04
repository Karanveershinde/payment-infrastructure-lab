package com.payflow.common.dto.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class ProviderModels {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorizeRequest {
        private String paymentId;
        private BigDecimal amount;
        private Currency currency;
        private String paymentMethodToken;
        private String customerEmail;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaptureRequest {
        private String providerTransactionId;
        private String paymentId;
        private BigDecimal amount;
        private Currency currency;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRequest {
        private String providerTransactionId;
        private String paymentId;
        private BigDecimal amount;
        private Currency currency;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProviderResponse {
        private boolean success;
        private String provider;
        private String providerTransactionId;
        private PaymentStatus status;
        private String errorCode;
        private String errorMessage;
        private long processingLatencyMs;
        private Instant timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureInjectionRequest {
        private String provider; // "PROVIDER_A", "PROVIDER_B", "PROVIDER_C" or "ALL"
        private Double successRate;
        private Integer minLatencyMs;
        private Integer maxLatencyMs;
        private Boolean forceOutage;
        private Boolean forceTimeout;
        private Boolean force500Error;
        private String forcedErrorCode;
    }
}
