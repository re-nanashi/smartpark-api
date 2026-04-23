package com.smartpark.api.parking.domain.repository;

import com.smartpark.api.parking.domain.model.ParkingLot;

import java.util.Optional;

public interface ParkingLotRepository {
    ParkingLot save(ParkingLot parkingLot);
    Optional<ParkingLot> findById(String lotId);
    Optional<ParkingLot> findByIdWithLock(String lotId);
    boolean existsById(String lotId);
}