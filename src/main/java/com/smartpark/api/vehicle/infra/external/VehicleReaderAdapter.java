package com.smartpark.api.vehicle.infra.external;

import com.smartpark.api.parking.application.contract.VehicleReader;
import com.smartpark.api.parking.application.contract.VehicleView;
import com.smartpark.api.vehicle.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VehicleReaderAdapter implements VehicleReader {
    private final VehicleRepository vehicleRepository;

    @Override
    public List<VehicleView> findAllByLicensePlates(List<String> licensePlates) {
        if (licensePlates == null || licensePlates.isEmpty()) {
            return Collections.emptyList();
        }

        return vehicleRepository.findAllByLicensePlateIn(licensePlates)
                .stream()
                .map(vehicle -> VehicleView.builder()
                        .licensePlate(vehicle.getLicensePlate())
                        .vehicleType(vehicle.getVehicleType())
                        .ownerName(vehicle.getOwnerName())
                        .build()
                )
                .toList();
    }

    @Override
    public boolean exists(String licensePlate) {
        return vehicleRepository.existsByLicensePlate(licensePlate);
    }
}