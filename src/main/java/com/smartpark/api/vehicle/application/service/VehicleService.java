package com.smartpark.api.vehicle.application.service;

import com.smartpark.api.shared.dto.PageResponse;
import com.smartpark.api.vehicle.api.dto.RegisterVehicleRequest;
import com.smartpark.api.vehicle.api.dto.VehicleResponse;
import org.springframework.data.domain.Pageable;

public interface VehicleService {
    VehicleResponse registerVehicle(RegisterVehicleRequest request);
    VehicleResponse getVehicle(String licensePlate);
    PageResponse<VehicleResponse> getAllVehicles(Pageable pageable);
}