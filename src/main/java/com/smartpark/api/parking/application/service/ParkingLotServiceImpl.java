package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.response.OccupancyResponse;
import com.smartpark.api.parking.api.dto.response.ParkingLotResponse;
import com.smartpark.api.parking.api.dto.request.RegisterParkingLotRequest;
import com.smartpark.api.parking.domain.model.ParkingLot;
import com.smartpark.api.parking.domain.repository.ParkingLotRepository;
import com.smartpark.api.shared.exception.ConflictException;
import com.smartpark.api.shared.exception.ErrorCode;
import com.smartpark.api.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingLotServiceImpl implements ParkingLotService {
    private final ParkingLotRepository parkingLotRepository;

    @Override
    @Transactional
    public ParkingLotResponse registerLot(RegisterParkingLotRequest request) {
        if (parkingLotRepository.existsById(request.lotId())) {
            throw new ConflictException(
                    "Parking lot with ID '" + request.lotId() + "' already exists",
                    ErrorCode.PARKING_LOT_ALREADY_EXISTS
            );
        }

        ParkingLot lot = ParkingLot.builder()
                .lotId(request.lotId())
                .location(request.location())
                .capacity(request.capacity())
                .occupiedSpaces(0)
                .build();

        ParkingLot saved = parkingLotRepository.save(lot);

        log.info("Registered parking lot '{}' at '{}' with capacity {}",
                saved.getLotId(), saved.getLocation(), saved.getCapacity());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OccupancyResponse getOccupancy(String lotId) {
        ParkingLot lot = parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new NotFoundException(
                        "Parking lot '" + lotId + "' not found",
                        ErrorCode.PARKING_LOT_NOT_FOUND
                ));

        int available = lot.getCapacity() - lot.getOccupiedSpaces();
        double pct = lot.getCapacity() > 0
                ? (lot.getOccupiedSpaces() * 100.0) / lot.getCapacity()
                : 0.0;

        return OccupancyResponse.builder()
                .lotId(lot.getLotId())
                .location(lot.getLocation())
                .capacity(lot.getCapacity())
                .occupiedSpaces(lot.getOccupiedSpaces())
                .availableSpaces(available)
                .occupancyPercentage(Math.round(pct * 100.0) / 100.0)
                .build();
    }

    private ParkingLotResponse toResponse(ParkingLot lot) {
        return ParkingLotResponse.builder()
                .lotId(lot.getLotId())
                .location(lot.getLocation())
                .capacity(lot.getCapacity())
                .occupiedSpaces(lot.getOccupiedSpaces())
                .availableSpaces(lot.getCapacity() - lot.getOccupiedSpaces())
                .build();
    }
}