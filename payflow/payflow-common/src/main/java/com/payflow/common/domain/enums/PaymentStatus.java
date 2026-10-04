package com.payflow.common.domain.enums;

/**
 * Payment lifecycle states managed by the PayFlow explicit state machine.
 */
public enum PaymentStatus {
    /**
     * Initial order created by merchant, waiting for orchestration.
     */
    CREATED,

    /**
     * In-flight with payment provider (authorization / charge initiated).
     */
    PROCESSING,

    /**
     * Payment authorized by the provider; funds placed on hold.
     */
    AUTHORIZED,

    /**
     * Funds successfully captured/settled. Terminal success state for normal payments.
     */
    CAPTURED,

    /**
     * Payment failed permanently or exhausted all retry attempts.
     */
    FAILED,

    /**
     * Payment cancelled by merchant before settlement.
     */
    CANCELLED,

    /**
     * Refund requested and currently pending provider execution.
     */
    REFUND_PENDING,

    /**
     * Funds refunded back to the customer. Terminal refund state.
     */
    REFUNDED;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELLED || this == REFUNDED;
    }
}
