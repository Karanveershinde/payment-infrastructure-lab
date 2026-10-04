package com.payflow.common.exception;

public class InvalidWebhookSignatureException extends PayFlowException {
    public InvalidWebhookSignatureException(String message) {
        super(message, "INVALID_WEBHOOK_SIGNATURE");
    }
}
