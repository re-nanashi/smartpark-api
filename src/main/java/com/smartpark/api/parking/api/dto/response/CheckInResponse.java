package com.smartpark.api.parking.api.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record CheckInResponse(
        String sessionId,
        String lotId,
        String licensePlate,
        Instant checkInTime,
        int remainingSpaces
) {}