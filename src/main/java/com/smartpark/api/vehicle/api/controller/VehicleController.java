package com.smartpark.api.vehicle.api.controller;

import com.smartpark.api.shared.dto.ApiResponse;
import com.smartpark.api.shared.dto.PageResponse;
import com.smartpark.api.vehicle.api.dto.RegisterVehicleRequest;
import com.smartpark.api.vehicle.api.dto.VehicleResponse;
import com.smartpark.api.vehicle.application.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleService vehicleService;

    /**
     * Register a new vehicle.
     * POST /api/v1/vehicles
     */
    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> registerVehicle(
            @RequestBody @Valid RegisterVehicleRequest request
    ) {
        VehicleResponse vehicle = vehicleService.registerVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Success", vehicle));
    }

    /**
     * Get vehicle information.
     * GET /api/v1/vehicles/{licensePlate}
     */
    @GetMapping("/{licensePlate}")
    public ResponseEntity<ApiResponse<VehicleResponse>> getVehicle(@PathVariable String licensePlate) {
        VehicleResponse vehicle = vehicleService.getVehicle(licensePlate);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Success", vehicle));
    }

    /**
     * Get a paginated list of vehicles.
     * GET /api/v1/vehicles
     * <p>
     * To use: pass 'page', 'size', and 'sort' as query parameters.
     * Example: /api/vehicles?page=0&size=20&sort=brand,asc
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VehicleResponse>>> getAllVehicles(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<VehicleResponse> vehicles = vehicleService.getAllVehicles(pageable);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Success", vehicles));
    }
}