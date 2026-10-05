package com.ledgerx.common.exception;

public class LedgerInvariantException extends RuntimeException {
    public LedgerInvariantException(String message) {
        super(message);
    }

    public LedgerInvariantException(String message, Throwable cause) {
        super(message, cause);
    }
}
