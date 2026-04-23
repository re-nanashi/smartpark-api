package com.smartpark.api.vehicle.infra.repository;

import com.smartpark.api.vehicle.domain.model.Vehicle;
import com.smartpark.api.vehicle.domain.repository.VehicleRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaVehicleRepository extends JpaRepository<Vehicle, String>, VehicleRepository {}