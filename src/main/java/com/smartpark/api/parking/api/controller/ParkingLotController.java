package com.smartpark.api.parking.api.controller;

import com.smartpark.api.parking.api.dto.response.OccupancyResponse;
import com.smartpark.api.parking.api.dto.response.ParkingLotResponse;
import com.smartpark.api.parking.api.dto.request.RegisterParkingLotRequest;
import com.smartpark.api.parking.application.contract.VehicleView;
import com.smartpark.api.parking.application.service.ParkingLotService;
import com.smartpark.api.parking.application.service.ParkingSessionService;
import com.smartpark.api.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lots")
@RequiredArgsConstructor
public class ParkingLotController {
    private final ParkingLotService parkingLotService;
    private final ParkingSessionService parkingSessionService;

    /**
     * Register a new parking lot.
     * POST /api/v1/lots
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ParkingLotResponse>> registerLot(
            @RequestBody @Valid RegisterParkingLotRequest request
    ) {
        ParkingLotResponse parkingLot = parkingLotService.registerLot(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Success", parkingLot));
    }

    /**
     * Get current occupancy and availability of a parking lot.
     * GET /api/v1/lots/{lotId}/occupancy
     */
    @GetMapping("/{lotId}/occupancy")
    public ResponseEntity<ApiResponse<OccupancyResponse>> getOccupancy(@PathVariable String lotId) {
        OccupancyResponse occupancy = parkingLotService.getOccupancy(lotId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Success", occupancy));
    }

    /**
     * Get all vehicles currently parked in a lot.
     * GET /api/v1/lots/{lotId}/vehicles
     */
    @GetMapping("/{lotId}/vehicles")
    public ResponseEntity<ApiResponse<Object>> getParkedVehicles(@PathVariable String lotId) {
        List<VehicleView> parkedVehicles = parkingSessionService.getParkedVehicles(lotId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Success", parkedVehicles));
    }
}