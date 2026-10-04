package com.payflow.common.exception;

public class IdempotencyConflictException extends PayFlowException {
    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super(String.format("A request with Idempotency-Key '%s' is currently processing. Concurrent duplicate request rejected.", idempotencyKey),
                "IDEMPOTENCY_CONFLICT");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
