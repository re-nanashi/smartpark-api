package com.smartpark.api.parking.api.dto.response;

import lombok.Builder;

@Builder
public record OccupancyResponse(
        String lotId,
        String location,
        int capacity,
        int occupiedSpaces,
        int availableSpaces,
        double occupancyPercentage
) {}