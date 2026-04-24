package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.response.OccupancyResponse;
import com.smartpark.api.parking.api.dto.response.ParkingLotResponse;
import com.smartpark.api.parking.api.dto.request.RegisterParkingLotRequest;

public interface ParkingLotService {
    ParkingLotResponse registerLot(RegisterParkingLotRequest request);
    OccupancyResponse getOccupancy(String lotId);
}