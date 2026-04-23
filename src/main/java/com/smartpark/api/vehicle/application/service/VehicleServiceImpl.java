package com.smartpark.api.vehicle.application.service;

import com.smartpark.api.shared.dto.PageResponse;
import com.smartpark.api.shared.exception.ConflictException;
import com.smartpark.api.shared.exception.ErrorCode;
import com.smartpark.api.shared.exception.NotFoundException;
import com.smartpark.api.vehicle.api.dto.RegisterVehicleRequest;
import com.smartpark.api.vehicle.api.dto.VehicleResponse;
import com.smartpark.api.vehicle.domain.model.Vehicle;
import com.smartpark.api.vehicle.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService{
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional
    public VehicleResponse registerVehicle(RegisterVehicleRequest request) {
        if (vehicleRepository.existsByLicensePlate(request.licensePlate())) {
            throw new ConflictException(
                    "Vehicle with license plate '" + request.licensePlate() + "' already exists",
                    ErrorCode.VEHICLE_ALREADY_EXISTS
            );
        }

        Vehicle vehicle = Vehicle.builder()
                .licensePlate(request.licensePlate())
                .vehicleType(request.vehicleType())
                .ownerName(request.ownerName())
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);

        log.info("Registered vehicle '{}' ({}) owned by '{}'",
                saved.getLicensePlate(), saved.getVehicleType(), saved.getOwnerName());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(String licensePlate) {
        Vehicle vehicle = vehicleRepository.findByLicensePlate(licensePlate)
                .orElseThrow(() -> new NotFoundException(
                        "Vehicle with license plate '" + licensePlate + "' not found",
                        ErrorCode.VEHICLE_NOT_FOUND
                ));

        return toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> getAllVehicles(Pageable pageable) {
        Page<VehicleResponse> vehicles = vehicleRepository.findAll(pageable)
                .map(this::toResponse);

        return toPageResponse(vehicles);
    }

    private VehicleResponse toResponse(Vehicle v) {
        return VehicleResponse.builder()
                .licensePlate(v.getLicensePlate())
                .vehicleType(v.getVehicleType())
                .ownerName(v.getOwnerName())
                .build();
    }

    private PageResponse<VehicleResponse> toPageResponse(Page<VehicleResponse> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}