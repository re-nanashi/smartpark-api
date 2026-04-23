package com.smartpark.api.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    // Core
    BUSINESS_ERROR("BUSINESS_ERROR"),
    RESOURCE_CONFLICT("RESOURCE_CONFLICT"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND"),

    // Vehicle
    VEHICLE_ALREADY_EXISTS("VEHICLE_ALREADY_EXISTS"),
    VEHICLE_NOT_FOUND("VEHICLE_NOT_FOUND");

    private final String value;
}