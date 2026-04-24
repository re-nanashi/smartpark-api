package com.smartpark.api.parking.domain.repository;

import com.smartpark.api.parking.domain.model.ParkingSession;

import java.util.List;
import java.util.Optional;

public interface ParkingSessionRepository {
    ParkingSession save(ParkingSession parkingSession);
    Optional<ParkingSession> findActiveByLicensePlate(String licensePlate);
    Optional<ParkingSession> findActiveByLotAndLicensePlate(String lotId, String licensePlate);
    List<ParkingSession> findAllActiveByLotId(String lotId);
}