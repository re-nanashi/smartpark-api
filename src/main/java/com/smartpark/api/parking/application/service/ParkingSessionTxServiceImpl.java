package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.response.CheckInResponse;
import com.smartpark.api.parking.api.dto.response.CheckOutResponse;
import com.smartpark.api.parking.application.contract.VehicleReader;
import com.smartpark.api.parking.application.contract.VehicleView;
import com.smartpark.api.parking.application.exception.DbLockTimeoutException;
import com.smartpark.api.parking.application.exception.LotFullException;
import com.smartpark.api.parking.application.exception.VehicleAlreadyParkedException;
import com.smartpark.api.parking.application.exception.VehicleNotParkedException;
import com.smartpark.api.parking.domain.model.ParkingLot;
import com.smartpark.api.parking.domain.model.ParkingSession;
import com.smartpark.api.parking.domain.repository.ParkingLotRepository;
import com.smartpark.api.parking.domain.repository.ParkingSessionRepository;
import com.smartpark.api.shared.exception.ErrorCode;
import com.smartpark.api.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingSessionTxServiceImpl implements ParkingSessionTxService {
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final VehicleReader vehicleReader;

    @Override
    @Transactional
    public CheckInResponse executeCheckIn(String lotId, String licensePlate) {
        ParkingLot lot = parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new NotFoundException(
                        "Parking lot '" + lotId + "' not found",
                        ErrorCode.PARKING_LOT_NOT_FOUND
                ));
        return  doCheckIn(lot, licensePlate);
    }

    @Override
    @Transactional
    public CheckOutResponse executeCheckOut(String lotId, String licensePlate) {
        ParkingLot lot = parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new NotFoundException(
                        "Parking lot '" + lotId + "' not found",
                        ErrorCode.PARKING_LOT_NOT_FOUND
                ));
        return doCheckOut(lot, licensePlate);
    }

    @Override
    @Transactional
    public CheckInResponse executeCheckInWithDbLock(String lotId, String licensePlate) {
        try {
            ParkingLot lot = parkingLotRepository.findByIdWithLock(lotId)
                    .orElseThrow(() -> new NotFoundException(
                            "Parking lot '" + lotId + "' not found",
                            ErrorCode.PARKING_LOT_NOT_FOUND
                    ));
            return doCheckIn(lot, licensePlate);
        } catch (CannotAcquireLockException | JpaSystemException | jakarta.persistence.LockTimeoutException ex) {
            throw new DbLockTimeoutException(
                    "DB pessimistic lock timed out for lot '" + lotId + "'", ex);
        }
    }

    @Override
    @Transactional
    public CheckOutResponse executeCheckOutWithDbLock(String lotId, String licensePlate) {
        try {
            ParkingLot lot = parkingLotRepository.findByIdWithLock(lotId)
                    .orElseThrow(() -> new NotFoundException(
                            "Parking lot '" + lotId + "' not found",
                            ErrorCode.PARKING_LOT_NOT_FOUND
                    ));
            return doCheckOut(lot, licensePlate);
        } catch (CannotAcquireLockException | JpaSystemException
                 | jakarta.persistence.LockTimeoutException ex) {
            throw new DbLockTimeoutException(
                    "DB pessimistic lock timed out for lot '" + lotId + "'", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleView> getParkedVehicles(String lotId) {
        // Check lot existence
        if (!parkingLotRepository.existsById(lotId)) {
            throw new NotFoundException(
                    "Parking lot '" + lotId + "' not found",
                    ErrorCode.PARKING_LOT_NOT_FOUND
            );
        }

        // Get all active license plates
        List<String> plates = parkingSessionRepository.findAllActiveByLotId(lotId)
                .stream()
                .map(ParkingSession::getLicensePlate)
                .toList();

        // Batch fetch vehicles in one query
        return vehicleReader.findAllByLicensePlates(plates);
    }

    private CheckInResponse doCheckIn(ParkingLot lot, String licensePlate) {
        // 1. Capacity guard
        if (lot.getOccupiedSpaces() >= lot.getCapacity()) {
            throw new LotFullException(
                    "Parking lot '" + lot.getLotId() + "' is full (" + lot.getCapacity() + "/" + lot.getCapacity() + ")"
            );
        }

        // 2. Vehicle must exist
        boolean vehicleExists = vehicleReader.exists(licensePlate);
        if (!vehicleExists) {
            throw new NotFoundException(
                    "Vehicle '" + licensePlate + "' not registered",
                    ErrorCode.VEHICLE_NOT_FOUND
            );
        }

        // 3. Vehicle must not already be parked anywhere
        parkingSessionRepository.findActiveByLicensePlate(licensePlate).ifPresent(existing ->  {
            throw new VehicleAlreadyParkedException(
                    "Vehicle '" + licensePlate + "' is already parked in lot '"
                            + existing.getParkingLot().getLotId() + "'");
        });

        // 4. Create session
        ParkingSession session = ParkingSession.builder()
                .parkingLot(lot)
                .licensePlate(licensePlate)
                .checkInTime(Instant.now())
                .active(true)
                .build();
        parkingSessionRepository.save(session);

        // 5. Increment occupancy
        lot.setOccupiedSpaces(lot.getOccupiedSpaces() + 1);
        parkingLotRepository.save(lot);

        int remaining = lot.getCapacity() - lot.getOccupiedSpaces();

        log.info("CHECK-IN  lot='{}' plate='{}' remaining={}", lot.getLotId(), licensePlate, remaining);

        return CheckInResponse.builder()
                .sessionId(session.getId())
                .lotId(lot.getLotId())
                .licensePlate(licensePlate)
                .checkInTime(session.getCheckInTime())
                .remainingSpaces(remaining)
                .build();
    }

    private CheckOutResponse doCheckOut(ParkingLot lot, String licensePlate) {
        // 1. Active session must exist for this vehicle in this lot
        ParkingSession session = parkingSessionRepository
                .findActiveByLotAndLicensePlate(lot.getLotId(), licensePlate)
                .orElseThrow(() -> new VehicleNotParkedException(
                        "Vehicle '" + licensePlate + "' has no active session in lot '" + lot.getLotId() + "'"));

        // 2. Close session
        session.setActive(false);
        session.setCheckOutTime(Instant.now());
        parkingSessionRepository.save(session);

        // 3. Decrement occupancy (never below zero)
        int newOccupied = Math.max(0, lot.getOccupiedSpaces() - 1);
        lot.setOccupiedSpaces(newOccupied);
        parkingLotRepository.save(lot);

        int available = lot.getCapacity() - lot.getOccupiedSpaces();

        log.info("CHECK-OUT lot='{}' plate='{}' available={}", lot.getLotId(), licensePlate, available);

        return CheckOutResponse.builder()
                .sessionId(session.getId())
                .lotId(lot.getLotId())
                .licensePlate(licensePlate)
                .checkInTime(session.getCheckInTime())
                .checkOutTime(session.getCheckOutTime())
                .availableSpaces(available)
                .build();
    }
}