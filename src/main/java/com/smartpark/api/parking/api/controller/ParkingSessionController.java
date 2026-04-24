package com.smartpark.api.parking.api.controller;

import com.smartpark.api.parking.api.dto.request.CheckInRequest;
import com.smartpark.api.parking.api.dto.request.CheckOutRequest;
import com.smartpark.api.parking.api.dto.response.CheckInResponse;
import com.smartpark.api.parking.api.dto.response.CheckOutResponse;
import com.smartpark.api.parking.application.service.ParkingSessionService;
import com.smartpark.api.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class ParkingSessionController {
    private final ParkingSessionService sessionService;

    /**
     * Check a vehicle into a parking lot.
     * POST /api/v1/sessions/check-in
     * <p>
     * Returns a definitive {@code 200 OK} or error on the same HTTP connection; no pending / polling states.
     * The response includes the remaining available spaces and the session ID for reference.
     */
    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(
            @RequestBody @Valid CheckInRequest request
    ) {
        CheckInResponse response = sessionService.checkIn(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Success", response));
    }

    /**
     * Check a vehicle out of a parking lot.
     * POST /api/v1/sessions/check-out
     * <p>
     * Returns a definitive {@code 200 OK} with session summary, or 409 if the vehicle has no active session in the specified lot.
     */
    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<CheckOutResponse>> checkOut(
            @RequestBody @Valid CheckOutRequest request
    ) {
        CheckOutResponse response = sessionService.checkOut(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>("Success", response));
    }
}