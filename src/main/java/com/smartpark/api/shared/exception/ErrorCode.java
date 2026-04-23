package com.smartpark.api.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    BUSINESS_ERROR("BUSINESS_ERROR");

    private final String value;
}