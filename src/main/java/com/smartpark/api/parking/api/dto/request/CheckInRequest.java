package com.smartpark.api.parking.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
        @NotBlank(message = "Lot ID must not be blank")
        String lotId,

        @NotBlank(message = "License plate must not be blank")
        String licensePlate
) {}