package com.smartpark.api.parking.application.exception;

import com.smartpark.api.shared.exception.ConflictException;
import com.smartpark.api.shared.exception.ErrorCode;

public class VehicleNotParkedException extends ConflictException {
    public VehicleNotParkedException(String message) {
        super(message, ErrorCode.VEHICLE_NOT_PARKED);
    }
}