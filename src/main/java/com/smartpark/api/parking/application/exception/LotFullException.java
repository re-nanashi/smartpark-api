package com.smartpark.api.parking.application.exception;

import com.smartpark.api.shared.exception.ConflictException;
import com.smartpark.api.shared.exception.ErrorCode;

public class LotFullException extends ConflictException {
    public LotFullException(String message) {
        super(message, ErrorCode.PARKING_LOT_FULL);
    }
}