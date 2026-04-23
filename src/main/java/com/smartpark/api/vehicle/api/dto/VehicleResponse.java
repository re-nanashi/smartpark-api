package com.smartpark.api.vehicle.api.dto;

import com.smartpark.api.vehicle.domain.model.VehicleType;
import lombok.Builder;

@Builder
public record VehicleResponse(
        String licensePlate,
        VehicleType vehicleType,
        String ownerName
) {}