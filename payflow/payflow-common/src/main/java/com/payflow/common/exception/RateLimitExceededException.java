package com.payflow.common.exception;

public class RateLimitExceededException extends PayFlowException {
    private final String merchantId;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String merchantId, long retryAfterSeconds) {
        super(String.format("Rate limit exceeded for merchant '%s'. Please retry after %d seconds.", merchantId, retryAfterSeconds),
                "RATE_LIMIT_EXCEEDED");
        this.merchantId = merchantId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
