package com.payflow.common.exception;

public class DuplicateWebhookException extends PayFlowException {
    private final String provider;
    private final String providerEventId;

    public DuplicateWebhookException(String provider, String providerEventId) {
        super(String.format("Webhook event '%s' from provider '%s' has already been received.", providerEventId, provider),
                "DUPLICATE_WEBHOOK");
        this.provider = provider;
        this.providerEventId = providerEventId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }
}
