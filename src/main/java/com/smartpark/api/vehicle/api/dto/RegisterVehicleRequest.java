package com.smartpark.api.vehicle.api.dto;

import com.smartpark.api.vehicle.domain.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterVehicleRequest(
        @NotBlank(message = "License plate must not be blank")
        @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "License plate may only contain letters, numbers, and dashes")
        @Size(min = 8, max = 20, message = "License plate must be between 8 and 20 characters")
        String licensePlate,

        @NotNull(message = "Vehicle type must not be null (CAR, MOTORCYCLE, TRUCK)")
        VehicleType vehicleType,

        @NotBlank(message = "Owner name must not be blank")
        @Pattern(regexp = "^[A-Za-z ]+$",
                message = "Owner name may only contain letters and spaces")
        @Size(min = 2, max = 20, message = "Owner name must be between 2 and 100 characters")
        String ownerName
) {}