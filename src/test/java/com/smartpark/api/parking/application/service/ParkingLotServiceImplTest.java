package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.response.OccupancyResponse;
import com.smartpark.api.parking.api.dto.response.ParkingLotResponse;
import com.smartpark.api.parking.api.dto.request.RegisterParkingLotRequest;
import com.smartpark.api.parking.domain.model.ParkingLot;
import com.smartpark.api.parking.domain.repository.ParkingLotRepository;
import com.smartpark.api.shared.exception.ConflictException;
import com.smartpark.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParkingLotServiceImpl Tests")
class ParkingLotServiceImplTest {
    @Mock
    private ParkingLotRepository parkingLotRepository;

    @InjectMocks
    private ParkingLotServiceImpl parkingLotService;

    private static final String LOT_ID = "LOT-001";
    private static final String LOCATION = "SM Ground Floor, Building A";
    private static final int CAPACITY = 50;

    private RegisterParkingLotRequest buildRequest() {
        return new RegisterParkingLotRequest(LOT_ID, LOCATION, CAPACITY);
    }

    private ParkingLot buildLot(int occupiedSpaces) {
        return ParkingLot.builder()
                .lotId(LOT_ID)
                .location(LOCATION)
                .capacity(CAPACITY)
                .occupiedSpaces(occupiedSpaces) // always start with 0
                .build();
    }

    @Nested
    @DisplayName("registerLot()")
    class RegisterLot {
        @Test
        @DisplayName("Should register and return a ParkingLotResponse when lot ID is unique")
        void shouldRegisterLot_whenLotIdIsUnique() {
            // Arrange
            RegisterParkingLotRequest request = buildRequest();
            ParkingLot saved = buildLot(0);

            given(parkingLotRepository.existsById(LOT_ID)).willReturn(false);
            given(parkingLotRepository.save(any(ParkingLot.class))).willReturn(saved);

            // Act
            ParkingLotResponse response = parkingLotService.registerLot(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.lotId()).isEqualTo(LOT_ID);
            assertThat(response.location()).isEqualTo(LOCATION);
            assertThat(response.capacity()).isEqualTo(CAPACITY);
            assertThat(response.occupiedSpaces()).isZero();
            assertThat(response.availableSpaces()).isEqualTo(CAPACITY); // capacity - 0

            then(parkingLotRepository).should().existsById(LOT_ID);
            then(parkingLotRepository).should().save(any(ParkingLot.class));
        }

        @Test
        @DisplayName("Should always initialise occupiedSpaces to 0 regardless of any request value")
        void shouldInitialiseOccupiedSpacesToZero() {
            // Arrange: save() returns the argument as-is so we inspect the built entity
            given(parkingLotRepository.existsById(LOT_ID)).willReturn(false);
            given(parkingLotRepository.save(any(ParkingLot.class))).willAnswer(inv -> inv.getArgument(0));

            // Act
            ParkingLotResponse response = parkingLotService.registerLot(buildRequest());

            // Assert: service hard-codes occupiedSpaces(0) in the builder
            assertThat(response.occupiedSpaces()).isZero();
            assertThat(response.availableSpaces()).isEqualTo(CAPACITY);
        }

        @Test
        @DisplayName("Should throw ConflictException when lot ID already exists")
        void shouldThrowConflictException_whenLotIdAlreadyExists() {
            // Arrange
            given(parkingLotRepository.existsById(LOT_ID)).willReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> parkingLotService.registerLot(buildRequest()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(LOT_ID);

            // save() must never be reached
            then(parkingLotRepository).should().existsById(LOT_ID);
            then(parkingLotRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("getOccupancy()")
    class GetOccupancy {
        @Test
        @DisplayName("Should return a fully populated OccupancyResponse when lot exists")
        void shouldReturnOccupancyResponse_whenLotExists() {
            // Arrange: 20 out of 50 spaces occupied
            ParkingLot lot = buildLot(20);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));

            // Act
            OccupancyResponse response = parkingLotService.getOccupancy(LOT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.lotId()).isEqualTo(LOT_ID);
            assertThat(response.location()).isEqualTo(LOCATION);
            assertThat(response.capacity()).isEqualTo(CAPACITY);
            assertThat(response.occupiedSpaces()).isEqualTo(20);
            assertThat(response.availableSpaces()).isEqualTo(30); // 50 - 20
            assertThat(response.occupancyPercentage()).isEqualTo(40.0); // (20 / 50) * 100

            then(parkingLotRepository).should().findById(LOT_ID);
        }

        @Test
        @DisplayName("Should compute occupancyPercentage correctly: (occupied / capacity) * 100, rounded to 2dp")
        void shouldComputeOccupancyPercentage_roundedToTwoDecimalPlaces() {
            // Arrange: 1 out of 3 > 33.333… > rounds to 33.33
            ParkingLot lot = ParkingLot.builder()
                    .lotId(LOT_ID).location(LOCATION)
                    .capacity(3).occupiedSpaces(1)
                    .build();
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));

            // Act
            OccupancyResponse response = parkingLotService.getOccupancy(LOT_ID);

            // Assert: Math.round(33.333… * 100.0) / 100.0 = 33.33
            assertThat(response.occupancyPercentage()).isEqualTo(33.33);
        }

        @Test
        @DisplayName("Should return occupancyPercentage of 0.0 when capacity is 0")
        void shouldReturnZeroOccupancyPercentage_whenCapacityIsZero() {
            // Arrange: capacity = 0 triggers the ternary else-branch (0.0)
            ParkingLot lot = ParkingLot.builder()
                    .lotId(LOT_ID).location(LOCATION)
                    .capacity(0).occupiedSpaces(0)
                    .build();
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));

            // Act
            OccupancyResponse response = parkingLotService.getOccupancy(LOT_ID);

            // Assert
            assertThat(response.occupancyPercentage()).isEqualTo(0.0);
            assertThat(response.availableSpaces()).isZero(); // 0 - 0
        }

        @Test
        @DisplayName("Should return occupancyPercentage of 100.0 when lot is completely full")
        void shouldReturn100OccupancyPercentage_whenLotIsFull() {
            // Arrange: all 50 spaces occupied
            ParkingLot lot = buildLot(CAPACITY);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));

            // Act
            OccupancyResponse response = parkingLotService.getOccupancy(LOT_ID);

            // Assert
            assertThat(response.occupancyPercentage()).isEqualTo(100.0);
            assertThat(response.availableSpaces()).isZero();
            assertThat(response.occupiedSpaces()).isEqualTo(CAPACITY);
        }

        @Test
        @DisplayName("Should return occupancyPercentage of 0.0 and full availableSpaces when lot is empty")
        void shouldReturnZeroOccupancy_whenLotIsEmpty() {
            // Arrange: 0 spaces occupied
            ParkingLot lot = buildLot(0);
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));

            // Act
            OccupancyResponse response = parkingLotService.getOccupancy(LOT_ID);

            // Assert
            assertThat(response.occupancyPercentage()).isEqualTo(0.0);
            assertThat(response.availableSpaces()).isEqualTo(CAPACITY);
            assertThat(response.occupiedSpaces()).isZero();
        }

        @Test
        @DisplayName("Should throw NotFoundException when lot ID does not exist")
        void shouldThrowNotFoundException_whenLotNotFound() {
            // Arrange
            given(parkingLotRepository.findById(LOT_ID)).willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> parkingLotService.getOccupancy(LOT_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(LOT_ID);

            then(parkingLotRepository).should().findById(LOT_ID);
        }
    }
}