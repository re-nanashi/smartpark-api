package com.smartpark.api.parking.application.contract;

import com.smartpark.api.vehicle.domain.model.VehicleType;
import lombok.Builder;

@Builder
public record VehicleView(
        String licensePlate,
        VehicleType vehicleType,
        String ownerName
) {}