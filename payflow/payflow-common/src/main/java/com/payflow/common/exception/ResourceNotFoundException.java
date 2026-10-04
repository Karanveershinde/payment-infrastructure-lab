package com.payflow.common.exception;

public class ResourceNotFoundException extends PayFlowException {
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s with identifier '%s' was not found.", resourceType, identifier), "RESOURCE_NOT_FOUND");
    }
}
