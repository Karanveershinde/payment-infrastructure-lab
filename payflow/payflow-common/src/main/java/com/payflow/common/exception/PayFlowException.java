package com.payflow.common.exception;

public class PayFlowException extends RuntimeException {

    private final String errorCode;

    public PayFlowException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public PayFlowException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
