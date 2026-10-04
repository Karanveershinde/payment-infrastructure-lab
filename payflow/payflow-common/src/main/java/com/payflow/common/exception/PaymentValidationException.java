package com.payflow.common.exception;

public class PaymentValidationException extends PayFlowException {
    public PaymentValidationException(String message) {
        super(message, "VALIDATION_ERROR");
    }
}
