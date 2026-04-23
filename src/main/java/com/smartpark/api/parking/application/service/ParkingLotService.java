package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.OccupancyResponse;
import com.smartpark.api.parking.api.dto.ParkingLotResponse;
import com.smartpark.api.parking.api.dto.RegisterParkingLotRequest;

public interface ParkingLotService {
    ParkingLotResponse registerLot(RegisterParkingLotRequest request);
    OccupancyResponse getOccupancy(String lotId);
}