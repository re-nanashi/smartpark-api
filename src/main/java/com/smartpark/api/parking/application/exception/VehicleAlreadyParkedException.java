package com.smartpark.api.parking.application.exception;

import com.smartpark.api.shared.exception.ConflictException;
import com.smartpark.api.shared.exception.ErrorCode;

public class VehicleAlreadyParkedException extends ConflictException {
    public VehicleAlreadyParkedException(String message) {
        super(message, ErrorCode.VEHICLE_ALREADY_PARKED);
    }
}