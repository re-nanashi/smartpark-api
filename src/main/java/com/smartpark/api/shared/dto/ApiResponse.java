package com.smartpark.api.shared.dto;

public record ApiResponse<T>(
        String message,
        T data
) {}