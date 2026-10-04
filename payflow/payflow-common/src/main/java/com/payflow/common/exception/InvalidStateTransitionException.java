package com.payflow.common.exception;

import com.payflow.common.domain.enums.PaymentStatus;

public class InvalidStateTransitionException extends PayFlowException {

    private final PaymentStatus fromStatus;
    private final PaymentStatus toStatus;

    public InvalidStateTransitionException(PaymentStatus fromStatus, PaymentStatus toStatus) {
        super(String.format("Illegal state transition attempted from [%s] to [%s]", fromStatus, toStatus), "ILLEGAL_STATE_TRANSITION");
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public PaymentStatus getFromStatus() {
        return fromStatus;
    }

    public PaymentStatus getToStatus() {
        return toStatus;
    }
}
