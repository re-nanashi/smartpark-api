package com.smartpark.api.parking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterParkingLotRequest(
        @NotBlank(message = "Lot ID must not be blank")
        @Size(max = 50, message = "Lot ID must not exceed 50 characters")
        String lotId,

        @NotBlank(message = "Location must not be blank")
        String location,

        @Positive(message = "Capacity must be a positive number")
        int capacity
) {}