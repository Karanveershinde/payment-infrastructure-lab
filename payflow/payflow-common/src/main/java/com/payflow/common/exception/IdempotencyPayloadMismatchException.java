package com.payflow.common.exception;

public class IdempotencyPayloadMismatchException extends PayFlowException {
    private final String idempotencyKey;

    public IdempotencyPayloadMismatchException(String idempotencyKey) {
        super(String.format("Idempotency key '%s' was previously used with a different request payload.", idempotencyKey),
                "IDEMPOTENCY_PAYLOAD_MISMATCH");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
