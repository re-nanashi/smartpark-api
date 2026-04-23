package com.smartpark.api.vehicle.domain.repository;

import com.smartpark.api.vehicle.domain.model.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface VehicleRepository {
    Vehicle save(Vehicle vehicle);
    Optional<Vehicle> findByLicensePlate(String licensePlate);
    Page<Vehicle> findAll(Pageable pageable);
    boolean existsByLicensePlate(String licensePlate);
}