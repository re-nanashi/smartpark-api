package com.smartpark.api.shared.exception;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(message, ErrorCode.RESOURCE_CONFLICT);
    }

    public ConflictException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}