package com.smartpark.api.shared.exception;

public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND);
    }

    public NotFoundException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}