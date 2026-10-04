package com.payflow.common.exception;

public class ProviderUnavailableException extends PayFlowException {
    private final String providerName;

    public ProviderUnavailableException(String providerName, String message) {
        super(String.format("Payment provider [%s] is unavailable: %s", providerName, message), "PROVIDER_UNAVAILABLE");
        this.providerName = providerName;
    }

    public ProviderUnavailableException(String providerName, String message, Throwable cause) {
        super(String.format("Payment provider [%s] is unavailable: %s", providerName, message), "PROVIDER_UNAVAILABLE", cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
