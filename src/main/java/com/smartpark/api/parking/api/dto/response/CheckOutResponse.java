package com.smartpark.api.parking.api.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record CheckOutResponse(
        String sessionId,
        String lotId,
        String licensePlate,
        Instant checkInTime,
        Instant checkOutTime,
        int availableSpaces
) {}