package com.ledgerx.common.domain.enums;

public enum ReconciliationStatus {
    PENDING,
    MATCHED,
    AMOUNT_MISMATCH,
    STATUS_MISMATCH,
    MISSING_INTERNAL,
    MISSING_PROVIDER,
    DUPLICATE_PROVIDER,
    CURRENCY_MISMATCH,
    LATE_SETTLEMENT,
    RESOLVED
}
