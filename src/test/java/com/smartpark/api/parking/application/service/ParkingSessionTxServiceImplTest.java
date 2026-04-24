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
import com.smartpark.api.vehicle.domain.model.VehicleType;
import jakarta.persistence.LockTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.jpa.JpaSystemException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParkingSessionTxServiceImpl Tests")
class ParkingSessionTxServiceImplTest {
    @Mock private ParkingSessionRepository parkingSessionRepository;
    @Mock private ParkingLotRepository parkingLotRepository;
    @Mock private VehicleReader vehicleReader;

    @InjectMocks
    private ParkingSessionTxServiceImpl txService;

    private static final String LOT_ID = "LOT-001";
    private static final String PLATE = "ABC-12345";
    private static final String LOCATION = "SM Ground Floor, Building A";

    private ParkingLot buildLot(int capacity, int occupiedSpaces) {
        return ParkingLot.builder()
                .lotId(LOT_ID)
                .location(LOCATION)
                .capacity(capacity)
                .occupiedSpaces(occupiedSpaces)
                .build();
    }

    private ParkingSession buildActiveSession(ParkingLot lot) {
        return ParkingSession.builder()
                .id("session-uuid-1")
                .parkingLot(lot)
                .licensePlate(PLATE)
                .checkInTime(Instant.parse("2026-04-24T10:00:00Z"))
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("executeCheckIn()")
    class ExecuteCheckIn {
        @Test
        @DisplayName("Should check in successfully when lot has space, vehicle exists, and no active session")
        void shouldCheckInSuccessfully() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(vehicleReader.exists(PLATE)).willReturn(true);
            given(parkingSessionRepository.findActiveByLicensePlate(PLATE)).willReturn(Optional.empty());
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> {
                        ParkingSession s = inv.getArgument(0);
                        s.setId("session-uuid-1");
                        return s;
                    });

            // Act
            CheckInResponse response = txService.executeCheckIn(LOT_ID, PLATE);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.sessionId()).isEqualTo("session-uuid-1");
            assertThat(response.lotId()).isEqualTo(LOT_ID);
            assertThat(response.licensePlate()).isEqualTo(PLATE);
            assertThat(response.checkInTime()).isNotNull();
            assertThat(response.remainingSpaces()).isEqualTo(39);  // 50 - 11

            // Lot occupancy must have been incremented and saved
            assertThat(lot.getOccupiedSpaces()).isEqualTo(11);
            then(parkingLotRepository).should().save(lot);
        }

        @Test
        @DisplayName("Should build session with active=true, current checkInTime, and lot reference")
        void shouldBuildSessionWithCorrectFields() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(vehicleReader.exists(PLATE)).willReturn(true);
            given(parkingSessionRepository.findActiveByLicensePlate(PLATE)).willReturn(Optional.empty());
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Instant beforeCall = Instant.now();

            // Act
            txService.executeCheckIn(LOT_ID, PLATE);

            // Assert: capture the session passed to save()
            ArgumentCaptor<ParkingSession> captor = ArgumentCaptor.forClass(ParkingSession.class);
            then(parkingSessionRepository).should().save(captor.capture());

            ParkingSession saved = captor.getValue();
            assertThat(saved.getParkingLot()).isSameAs(lot);
            assertThat(saved.getLicensePlate()).isEqualTo(PLATE);
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getCheckInTime()).isAfterOrEqualTo(beforeCall);
            assertThat(saved.getCheckOutTime()).isNull();
        }

        @Test
        @DisplayName("Should throw NotFoundException with PARKING_LOT_NOT_FOUND when lot does not exist")
        void shouldThrowNotFoundException_whenLotNotFound() {
            // Arrange
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.empty());

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> txService.executeCheckIn(LOT_ID, PLATE),
                    NotFoundException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex).hasMessageContaining(LOT_ID);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARKING_LOT_NOT_FOUND);

            // No downstream calls should happen
            then(vehicleReader).shouldHaveNoInteractions();
            then(parkingSessionRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Should throw LotFullException when occupiedSpaces equals capacity")
        void shouldThrowLotFullException_whenLotIsFull() {
            // Arrange: 50/50
            ParkingLot fullLot = buildLot(50, 50);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(fullLot));

            // Act
            LotFullException ex = catchThrowableOfType(
                    () -> txService.executeCheckIn(LOT_ID, PLATE),
                    LotFullException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex).hasMessageContaining(LOT_ID);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARKING_LOT_FULL);

            // Guard must short-circuit BEFORE any writes
            then(vehicleReader).shouldHaveNoInteractions();
            then(parkingSessionRepository).shouldHaveNoInteractions();
            then(parkingLotRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Should throw LotFullException when occupiedSpaces exceeds capacity (defensive)")
        void shouldThrowLotFullException_whenOccupiedExceedsCapacity() {
            // Arrange: defensive branch: 51 >= 50
            ParkingLot overFullLot = buildLot(50, 51);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(overFullLot));

            // Act & Assert
            assertThatThrownBy(() -> txService.executeCheckIn(LOT_ID, PLATE))
                    .isInstanceOf(LotFullException.class);
        }

        @Test
        @DisplayName("Should throw NotFoundException with VEHICLE_NOT_FOUND when vehicle is not registered")
        void shouldThrowNotFoundException_whenVehicleNotRegistered() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(vehicleReader.exists(PLATE)).willReturn(false);

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> txService.executeCheckIn(LOT_ID, PLATE),
                    NotFoundException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex).hasMessageContaining(PLATE);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND);

            // No session work must happen
            then(parkingSessionRepository).shouldHaveNoInteractions();
            then(parkingLotRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Should throw VehicleAlreadyParkedException when vehicle has an active session elsewhere")
        void shouldThrowVehicleAlreadyParkedException_whenVehicleAlreadyParked() {
            // Arrange
            ParkingLot lot       = buildLot(50, 10);
            ParkingLot otherLot  = ParkingLot.builder().lotId("LOT-OTHER").build();
            ParkingSession activeElsewhere = ParkingSession.builder()
                    .parkingLot(otherLot)
                    .licensePlate(PLATE)
                    .active(true)
                    .build();

            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(vehicleReader.exists(PLATE)).willReturn(true);
            given(parkingSessionRepository.findActiveByLicensePlate(PLATE))
                    .willReturn(Optional.of(activeElsewhere));

            // Act
            VehicleAlreadyParkedException ex = catchThrowableOfType(
                    () -> txService.executeCheckIn(LOT_ID, PLATE),
                    VehicleAlreadyParkedException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex).hasMessageContaining(PLATE).hasMessageContaining("LOT-OTHER");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VEHICLE_ALREADY_PARKED);

            // No session/lot writes
            then(parkingSessionRepository).should(never()).save(any());
            then(parkingLotRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Should correctly compute remainingSpaces after increment")
        void shouldComputeRemainingSpaces_afterIncrement() {
            // Arrange: 3/10 -> after check-in 4/10 -> remaining 6
            ParkingLot lot = buildLot(10, 3);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(vehicleReader.exists(PLATE)).willReturn(true);
            given(parkingSessionRepository.findActiveByLicensePlate(PLATE)).willReturn(Optional.empty());
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // Act
            CheckInResponse response = txService.executeCheckIn(LOT_ID, PLATE);

            // Assert
            assertThat(response.remainingSpaces()).isEqualTo(6);
            assertThat(lot.getOccupiedSpaces()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("executeCheckOut()")
    class ExecuteCheckOut {
        @Test
        @DisplayName("Should check out successfully and close the active session")
        void shouldCheckOutSuccessfully() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            ParkingSession active = buildActiveSession(lot);

            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(parkingSessionRepository.findActiveByLotAndLicensePlate(LOT_ID, PLATE))
                    .willReturn(Optional.of(active));
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // Act
            CheckOutResponse response = txService.executeCheckOut(LOT_ID, PLATE);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.sessionId()).isEqualTo("session-uuid-1");
            assertThat(response.lotId()).isEqualTo(LOT_ID);
            assertThat(response.licensePlate()).isEqualTo(PLATE);
            assertThat(response.checkInTime()).isEqualTo(active.getCheckInTime());
            assertThat(response.checkOutTime()).isNotNull();
            assertThat(response.availableSpaces()).isEqualTo(41);  // 50 - 9

            // Session closed
            assertThat(active.isActive()).isFalse();
            assertThat(active.getCheckOutTime()).isNotNull();

            // Lot decremented and saved
            assertThat(lot.getOccupiedSpaces()).isEqualTo(9);
            then(parkingLotRepository).should().save(lot);
        }

        @Test
        @DisplayName("Should throw NotFoundException with PARKING_LOT_NOT_FOUND when lot does not exist")
        void shouldThrowNotFoundException_whenLotNotFound() {
            // Arrange
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.empty());

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> txService.executeCheckOut(LOT_ID, PLATE),
                    NotFoundException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARKING_LOT_NOT_FOUND);
            then(parkingSessionRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Should throw VehicleNotParkedException when no active session exists for the vehicle in the lot")
        void shouldThrowVehicleNotParkedException_whenNoActiveSession() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(parkingSessionRepository.findActiveByLotAndLicensePlate(LOT_ID, PLATE))
                    .willReturn(Optional.empty());

            // Act
            VehicleNotParkedException ex = catchThrowableOfType(
                    () -> txService.executeCheckOut(LOT_ID, PLATE),
                    VehicleNotParkedException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex).hasMessageContaining(PLATE).hasMessageContaining(LOT_ID);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VEHICLE_NOT_PARKED);
            then(parkingLotRepository).should(never()).save(any());
            then(parkingSessionRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Should clamp occupiedSpaces to zero when decrementing from zero (defensive Math.max)")
        void shouldClampOccupiedSpacesToZero_whenAlreadyZero() {
            // Arrange — pathological state: session exists but lot shows 0 occupied
            ParkingLot lot = buildLot(50, 0);
            ParkingSession active = buildActiveSession(lot);

            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(parkingSessionRepository.findActiveByLotAndLicensePlate(LOT_ID, PLATE))
                    .willReturn(Optional.of(active));
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // Act
            CheckOutResponse response = txService.executeCheckOut(LOT_ID, PLATE);

            // Assert: Math.max(0, 0 - 1) = 0
            assertThat(lot.getOccupiedSpaces()).isZero();
            assertThat(response.availableSpaces()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should mark the session inactive and set checkOutTime before saving")
        void shouldMarkSessionInactiveAndSetCheckOutTime() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            ParkingSession active = buildActiveSession(lot);

            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
            given(parkingSessionRepository.findActiveByLotAndLicensePlate(LOT_ID, PLATE))
                    .willReturn(Optional.of(active));
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            Instant beforeCall = Instant.now();

            // Act
            txService.executeCheckOut(LOT_ID, PLATE);

            // Assert
            ArgumentCaptor<ParkingSession> captor = ArgumentCaptor.forClass(ParkingSession.class);
            then(parkingSessionRepository).should().save(captor.capture());
            ParkingSession saved = captor.getValue();

            assertThat(saved.isActive()).isFalse();
            assertThat(saved.getCheckOutTime()).isAfterOrEqualTo(beforeCall);
        }
    }

    @Nested
    @DisplayName("executeCheckInWithDbLock()")
    class ExecuteCheckInWithDbLock {
        @Test
        @DisplayName("Should delegate to doCheckIn when lock is acquired successfully")
        void shouldCheckInSuccessfully_withDbLock() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            given(parkingLotRepository.findByIdWithLock(LOT_ID)).willReturn(Optional.of(lot));
            given(vehicleReader.exists(PLATE)).willReturn(true);
            given(parkingSessionRepository.findActiveByLicensePlate(PLATE)).willReturn(Optional.empty());
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> {
                        ParkingSession s = inv.getArgument(0);
                        s.setId("session-uuid-1");
                        return s;
                    });

            // Act
            CheckInResponse response = txService.executeCheckInWithDbLock(LOT_ID, PLATE);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.sessionId()).isEqualTo("session-uuid-1");
            then(parkingLotRepository).should().findByIdWithLock(LOT_ID);
            then(parkingLotRepository).should(never()).findById(LOT_ID);
        }

        @Test
        @DisplayName("Should throw NotFoundException with PARKING_LOT_NOT_FOUND when lock lookup returns empty")
        void shouldThrowNotFoundException_whenLotNotFound_withLock() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID)).willReturn(Optional.empty());

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> txService.executeCheckInWithDbLock(LOT_ID, PLATE),
                    NotFoundException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARKING_LOT_NOT_FOUND);
        }

        @Test
        @DisplayName("Should wrap CannotAcquireLockException in DbLockTimeoutException")
        void shouldWrap_CannotAcquireLockException() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID))
                    .willThrow(new CannotAcquireLockException("boom"));

            // Act & Assert
            assertThatThrownBy(() -> txService.executeCheckInWithDbLock(LOT_ID, PLATE))
                    .isInstanceOf(DbLockTimeoutException.class)
                    .hasMessageContaining(LOT_ID)
                    .hasCauseInstanceOf(CannotAcquireLockException.class);
        }

        @Test
        @DisplayName("Should wrap JpaSystemException in DbLockTimeoutException")
        void shouldWrap_JpaSystemException() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID))
                    .willThrow(new JpaSystemException(new RuntimeException("jpa failure")));

            // Act & Assert
            assertThatThrownBy(() -> txService.executeCheckInWithDbLock(LOT_ID, PLATE))
                    .isInstanceOf(DbLockTimeoutException.class)
                    .hasMessageContaining(LOT_ID)
                    .hasCauseInstanceOf(JpaSystemException.class);
        }

        @Test
        @DisplayName("Should wrap jakarta.persistence.LockTimeoutException in DbLockTimeoutException")
        void shouldWrap_JakartaLockTimeoutException() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID))
                    .willThrow(new LockTimeoutException("lock timed out"));

            // Act & Assert
            assertThatThrownBy(() -> txService.executeCheckInWithDbLock(LOT_ID, PLATE))
                    .isInstanceOf(DbLockTimeoutException.class)
                    .hasMessageContaining(LOT_ID)
                    .hasCauseInstanceOf(LockTimeoutException.class);
        }

        @Test
        @DisplayName("Should NOT wrap NotFoundException; it must propagate unchanged")
        void shouldNotWrap_NotFoundException() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID)).willReturn(Optional.empty());

            // Act & Assert — NotFoundException propagates, it is not wrapped in DbLockTimeoutException
            assertThatThrownBy(() -> txService.executeCheckInWithDbLock(LOT_ID, PLATE))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("executeCheckOutWithDbLock()")
    class ExecuteCheckOutWithDbLock {
        @Test
        @DisplayName("Should delegate to doCheckOut when lock is acquired successfully")
        void shouldCheckOutSuccessfully_withDbLock() {
            // Arrange
            ParkingLot lot = buildLot(50, 10);
            ParkingSession active = buildActiveSession(lot);
            given(parkingLotRepository.findByIdWithLock(LOT_ID)).willReturn(Optional.of(lot));
            given(parkingSessionRepository.findActiveByLotAndLicensePlate(LOT_ID, PLATE))
                    .willReturn(Optional.of(active));
            given(parkingSessionRepository.save(any(ParkingSession.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // Act
            CheckOutResponse response = txService.executeCheckOutWithDbLock(LOT_ID, PLATE);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.availableSpaces()).isEqualTo(41);
            then(parkingLotRepository).should().findByIdWithLock(LOT_ID);
        }

        @Test
        @DisplayName("Should throw NotFoundException when lock lookup returns empty")
        void shouldThrowNotFoundException_whenLotNotFound_withLock() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID)).willReturn(Optional.empty());

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> txService.executeCheckOutWithDbLock(LOT_ID, PLATE),
                    NotFoundException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARKING_LOT_NOT_FOUND);
        }

        @Test
        @DisplayName("Should wrap CannotAcquireLockException in DbLockTimeoutException")
        void shouldWrap_CannotAcquireLockException() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID))
                    .willThrow(new CannotAcquireLockException("boom"));

            // Act & Assert
            assertThatThrownBy(() -> txService.executeCheckOutWithDbLock(LOT_ID, PLATE))
                    .isInstanceOf(DbLockTimeoutException.class)
                    .hasCauseInstanceOf(CannotAcquireLockException.class);
        }

        @Test
        @DisplayName("Should wrap JpaSystemException in DbLockTimeoutException")
        void shouldWrap_JpaSystemException() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID))
                    .willThrow(new JpaSystemException(new RuntimeException("jpa failure")));

            // Act & Assert
            assertThatThrownBy(() -> txService.executeCheckOutWithDbLock(LOT_ID, PLATE))
                    .isInstanceOf(DbLockTimeoutException.class)
                    .hasCauseInstanceOf(JpaSystemException.class);
        }

        @Test
        @DisplayName("Should wrap jakarta.persistence.LockTimeoutException in DbLockTimeoutException")
        void shouldWrap_JakartaLockTimeoutException() {
            // Arrange
            given(parkingLotRepository.findByIdWithLock(LOT_ID))
                    .willThrow(new LockTimeoutException("lock timed out"));

            // Act & Assert
            assertThatThrownBy(() -> txService.executeCheckOutWithDbLock(LOT_ID, PLATE))
                    .isInstanceOf(DbLockTimeoutException.class)
                    .hasCauseInstanceOf(LockTimeoutException.class);
        }
    }

    @Nested
    @DisplayName("getParkedVehicles()")
    class GetParkedVehicles {
        @Test
        @DisplayName("Should return vehicle views for all active session plates")
        void shouldReturnVehicleViews_forActiveSessions() {
            // Arrange
            ParkingLot lot = buildLot(50, 2);
            ParkingSession s1 = ParkingSession.builder()
                    .parkingLot(lot).licensePlate("ABC-12345").active(true).build();
            ParkingSession s2 = ParkingSession.builder()
                    .parkingLot(lot).licensePlate("XYZ-56789").active(true).build();

            VehicleView v1 = VehicleView.builder()
                    .licensePlate("ABC-12345").vehicleType(VehicleType.CAR).ownerName("Alice").build();
            VehicleView v2 = VehicleView.builder()
                    .licensePlate("XYZ-56789").vehicleType(VehicleType.MOTORCYCLE).ownerName("Bob").build();

            given(parkingLotRepository.existsById(LOT_ID)).willReturn(true);
            given(parkingSessionRepository.findAllActiveByLotId(LOT_ID)).willReturn(List.of(s1, s2));
            given(vehicleReader.findAllByLicensePlates(List.of("ABC-12345", "XYZ-56789")))
                    .willReturn(List.of(v1, v2));

            // Act
            List<VehicleView> result = txService.getParkedVehicles(LOT_ID);

            // Assert
            assertThat(result).containsExactly(v1, v2);
            then(vehicleReader).should().findAllByLicensePlates(List.of("ABC-12345", "XYZ-56789"));
        }

        @Test
        @DisplayName("Should return an empty list when there are no active sessions")
        void shouldReturnEmptyList_whenNoActiveSessions() {
            // Arrange
            given(parkingLotRepository.existsById(LOT_ID)).willReturn(true);
            given(parkingSessionRepository.findAllActiveByLotId(LOT_ID)).willReturn(List.of());
            given(vehicleReader.findAllByLicensePlates(List.of())).willReturn(List.of());

            // Act
            List<VehicleView> result = txService.getParkedVehicles(LOT_ID);

            // Assert
            assertThat(result).isEmpty();
            then(vehicleReader).should().findAllByLicensePlates(List.of());
        }

        @Test
        @DisplayName("Should throw NotFoundException with PARKING_LOT_NOT_FOUND when lot does not exist")
        void shouldThrowNotFoundException_whenLotDoesNotExist() {
            // Arrange
            given(parkingLotRepository.existsById(LOT_ID)).willReturn(false);

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> txService.getParkedVehicles(LOT_ID),
                    NotFoundException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARKING_LOT_NOT_FOUND);
            then(parkingSessionRepository).shouldHaveNoInteractions();
            then(vehicleReader).shouldHaveNoInteractions();
        }
    }
}