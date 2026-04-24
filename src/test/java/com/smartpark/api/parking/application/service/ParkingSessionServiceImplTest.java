package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.request.CheckInRequest;
import com.smartpark.api.parking.api.dto.request.CheckOutRequest;
import com.smartpark.api.parking.api.dto.response.CheckInResponse;
import com.smartpark.api.parking.api.dto.response.CheckOutResponse;
import com.smartpark.api.parking.application.contract.VehicleView;
import com.smartpark.api.parking.application.exception.DbLockTimeoutException;
import com.smartpark.api.parking.application.exception.LotFullException;
import com.smartpark.api.parking.application.exception.TooManyRequestException;
import com.smartpark.api.parking.application.exception.VehicleAlreadyParkedException;
import com.smartpark.api.shared.exception.NotFoundException;
import com.smartpark.api.vehicle.domain.model.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParkingSessionServiceImpl Tests")
class ParkingSessionServiceImplTest {
    private static final String LOT_ID = "LOT-001";
    private static final String PLATE = "ABC-12345";
    private static final String LOCK_KEY = "smartpark:lot:" + LOT_ID;

    @Mock private RedissonClient redissonClient;
    @Mock private ParkingSessionTxService txService;
    @Mock private RLock rLock;

    @InjectMocks
    private ParkingSessionServiceImpl sessionService;

    private CheckInResponse sampleCheckInResponse() {
        return CheckInResponse.builder()
                .sessionId("session-1")
                .lotId(LOT_ID)
                .licensePlate(PLATE)
                .checkInTime(Instant.parse("2026-04-24T10:00:00Z"))
                .remainingSpaces(39)
                .build();
    }

    private CheckOutResponse sampleCheckOutResponse() {
        return CheckOutResponse.builder()
                .sessionId("session-1")
                .lotId(LOT_ID)
                .licensePlate(PLATE)
                .checkInTime(Instant.parse("2026-04-24T10:00:00Z"))
                .checkOutTime(Instant.parse("2026-04-24T12:00:00Z"))
                .availableSpaces(40)
                .build();
    }

    @Nested
    @DisplayName("checkIn()")
    class CheckIn {
        @Test
        @DisplayName("Should check in via Redis lock when acquired and unlock afterwards")
        void shouldCheckIn_viaRedisLock() throws InterruptedException {
            // Arrange
            CheckInResponse expected = sampleCheckInResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(eq(0L), eq(200L), eq(TimeUnit.MILLISECONDS))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckIn(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckInResponse response = sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
            then(txService).should().executeCheckIn(LOT_ID, PLATE);
            then(txService).should(never()).executeCheckInWithDbLock(any(), any());
            then(rLock).should().unlock();
        }

        @Test
        @DisplayName("Should fall back to DB lock when Redis reports BUSY (tryLock returns false)")
        void shouldFallBackToDbLock_whenRedisBusy() throws InterruptedException {
            // Arrange
            CheckInResponse expected = sampleCheckInResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
            // No unlock expected — lock was never acquired, so isHeldByCurrentThread() == false
            given(txService.executeCheckInWithDbLock(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckInResponse response = sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
            then(txService).should(never()).executeCheckIn(any(), any());
            then(txService).should().executeCheckInWithDbLock(LOT_ID, PLATE);
        }

        @Test
        @DisplayName("Should fall back to DB lock when Redis is UNAVAILABLE (RedisException)")
        void shouldFallBackToDbLock_whenRedisUnavailable() throws InterruptedException {
            // Arrange
            CheckInResponse expected = sampleCheckInResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .willThrow(new RedisException("Redis down"));
            given(txService.executeCheckInWithDbLock(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckInResponse response = sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
            then(txService).should().executeCheckInWithDbLock(LOT_ID, PLATE);
        }

        @Test
        @DisplayName("Should fall back to DB lock when interrupted while acquiring Redis lock")
        void shouldFallBackToDbLock_whenInterrupted() throws InterruptedException {
            // Arrange
            CheckInResponse expected = sampleCheckInResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .willThrow(new InterruptedException("interrupted"));
            given(txService.executeCheckInWithDbLock(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckInResponse response = sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE));

            // Assert: InterruptedException is converted to RedisBusyException internally, then caught
            assertThat(response).isSameAs(expected);
            then(txService).should().executeCheckInWithDbLock(LOT_ID, PLATE);
            assertThat(Thread.interrupted()).isTrue();
        }

        @Test
        @DisplayName("Should throw TooManyRequestException when both Redis and DB locks fail")
        void shouldThrowTooManyRequest_whenBothTiersFail() throws InterruptedException {
            // Arrange: Redis busy AND DB lock times out
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
            given(txService.executeCheckInWithDbLock(LOT_ID, PLATE))
                    .willThrow(new DbLockTimeoutException("db timeout"));

            // Act & Assert
            assertThatThrownBy(() -> sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE)))
                    .isInstanceOf(TooManyRequestException.class)
                    .hasMessageContaining(LOT_ID);
        }

        @Test
        @DisplayName("Should propagate business exceptions from Redis path unchanged (e.g. LotFullException)")
        void shouldPropagateBusinessException_fromRedisPath() throws InterruptedException {
            // Arrange: lock acquired, but business rule fails
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckIn(LOT_ID, PLATE))
                    .willThrow(new LotFullException("lot full"));

            // Act & Assert: must NOT fall back; business exceptions propagate
            assertThatThrownBy(() -> sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE)))
                    .isInstanceOf(LotFullException.class);

            then(txService).should(never()).executeCheckInWithDbLock(any(), any());
            then(rLock).should().unlock();   // lock still released in finally
        }

        @Test
        @DisplayName("Should propagate NotFoundException from Redis path unchanged")
        void shouldPropagateNotFoundException_fromRedisPath() throws InterruptedException {
            // Arrange
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckIn(LOT_ID, PLATE))
                    .willThrow(new NotFoundException("lot not found"));

            // Act & Assert
            assertThatThrownBy(() -> sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE)))
                    .isInstanceOf(NotFoundException.class);
            then(rLock).should().unlock();
        }

        @Test
        @DisplayName("Should propagate business exceptions from DB path unchanged (e.g. VehicleAlreadyParkedException)")
        void shouldPropagateBusinessException_fromDbPath() throws InterruptedException {
            // Arrange: Redis busy, DB path throws business exception
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
            given(txService.executeCheckInWithDbLock(LOT_ID, PLATE))
                    .willThrow(new VehicleAlreadyParkedException("already parked"));

            // Act & Assert
            assertThatThrownBy(() -> sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE)))
                    .isInstanceOf(VehicleAlreadyParkedException.class);
        }

        @Test
        @DisplayName("Should release Redis lock even when executeCheckIn throws")
        void shouldReleaseRedisLock_onException() throws InterruptedException {
            // Arrange
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckIn(LOT_ID, PLATE))
                    .willThrow(new LotFullException("full"));

            // Act
            assertThatThrownBy(() -> sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE)))
                    .isInstanceOf(LotFullException.class);

            // Assert: unlock still called via finally
            then(rLock).should().unlock();
        }

        @Test
        @DisplayName("Should not fail the response when Redis unlock itself throws (logged but swallowed)")
        void shouldSwallowUnlockFailure() throws InterruptedException {
            // Arrange
            CheckInResponse expected = sampleCheckInResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckIn(LOT_ID, PLATE)).willReturn(expected);
            willThrow(new RuntimeException("unlock failed")).given(rLock).unlock();

            // Act: work already committed, unlock failure must not surface
            CheckInResponse response = sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
        }

        @Test
        @DisplayName("Should NOT attempt unlock when lock is not held by current thread")
        void shouldNotUnlock_whenLockNotHeldByCurrentThread() throws InterruptedException {
            // Arrange
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(false);
            given(txService.executeCheckIn(LOT_ID, PLATE)).willReturn(sampleCheckInResponse());

            // Act
            sessionService.checkIn(new CheckInRequest(LOT_ID, PLATE));

            // Assert
            then(rLock).should(never()).unlock();
        }
    }

    @Nested
    @DisplayName("checkOut()")
    class CheckOut {
        @Test
        @DisplayName("Should check out via Redis lock when acquired and unlock afterwards")
        void shouldCheckOut_viaRedisLock() throws InterruptedException {
            // Arrange
            CheckOutResponse expected = sampleCheckOutResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckOut(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckOutResponse response = sessionService.checkOut(new CheckOutRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
            then(txService).should().executeCheckOut(LOT_ID, PLATE);
            then(txService).should(never()).executeCheckOutWithDbLock(any(), any());
            then(rLock).should().unlock();
        }

        @Test
        @DisplayName("Should fall back to DB lock when Redis reports BUSY")
        void shouldFallBackToDbLock_whenRedisBusy() throws InterruptedException {
            // Arrange
            CheckOutResponse expected = sampleCheckOutResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
            given(txService.executeCheckOutWithDbLock(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckOutResponse response = sessionService.checkOut(new CheckOutRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
            then(txService).should().executeCheckOutWithDbLock(LOT_ID, PLATE);
        }

        @Test
        @DisplayName("Should fall back to DB lock when Redis is UNAVAILABLE (RedisException)")
        void shouldFallBackToDbLock_whenRedisUnavailable() throws InterruptedException {
            // Arrange
            CheckOutResponse expected = sampleCheckOutResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .willThrow(new RedisException("Redis down"));
            given(txService.executeCheckOutWithDbLock(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckOutResponse response = sessionService.checkOut(new CheckOutRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
            then(txService).should().executeCheckOutWithDbLock(LOT_ID, PLATE);
        }

        @Test
        @DisplayName("Should fall back to DB lock when interrupted while acquiring Redis lock")
        void shouldFallBackToDbLock_whenInterrupted() throws InterruptedException {
            // Arrange
            CheckOutResponse expected = sampleCheckOutResponse();
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .willThrow(new InterruptedException("interrupted"));
            given(txService.executeCheckOutWithDbLock(LOT_ID, PLATE)).willReturn(expected);

            // Act
            CheckOutResponse response = sessionService.checkOut(new CheckOutRequest(LOT_ID, PLATE));

            // Assert
            assertThat(response).isSameAs(expected);
            assertThat(Thread.interrupted()).isTrue();
        }

        @Test
        @DisplayName("Should throw TooManyRequestException when both Redis and DB locks fail")
        void shouldThrowTooManyRequest_whenBothTiersFail() throws InterruptedException {
            // Arrange
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);
            given(txService.executeCheckOutWithDbLock(LOT_ID, PLATE))
                    .willThrow(new DbLockTimeoutException("db timeout"));

            // Act & Assert
            assertThatThrownBy(() -> sessionService.checkOut(new CheckOutRequest(LOT_ID, PLATE)))
                    .isInstanceOf(TooManyRequestException.class)
                    .hasMessageContaining(LOT_ID);
        }

        @Test
        @DisplayName("Should propagate business exceptions from Redis path unchanged")
        void shouldPropagateBusinessException_fromRedisPath() throws InterruptedException {
            // Arrange
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckOut(LOT_ID, PLATE))
                    .willThrow(new NotFoundException("not found"));

            // Act & Assert
            assertThatThrownBy(() -> sessionService.checkOut(new CheckOutRequest(LOT_ID, PLATE)))
                    .isInstanceOf(NotFoundException.class);
            then(txService).should(never()).executeCheckOutWithDbLock(any(), any());
            then(rLock).should().unlock();
        }

        @Test
        @DisplayName("Should release Redis lock even when executeCheckOut throws")
        void shouldReleaseRedisLock_onException() throws InterruptedException {
            // Arrange
            given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
            given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(rLock.isHeldByCurrentThread()).willReturn(true);
            given(txService.executeCheckOut(LOT_ID, PLATE))
                    .willThrow(new NotFoundException("not found"));

            // Act
            assertThatThrownBy(() -> sessionService.checkOut(new CheckOutRequest(LOT_ID, PLATE)))
                    .isInstanceOf(NotFoundException.class);

            // Assert
            then(rLock).should().unlock();
        }
    }

    @Nested
    @DisplayName("getParkedVehicles()")
    class GetParkedVehicles {
        @Test
        @DisplayName("Should delegate directly to txService without any locking")
        void shouldDelegateToTxService() {
            // Arrange
            List<VehicleView> expected = List.of(
                    VehicleView.builder()
                            .licensePlate(PLATE).vehicleType(VehicleType.CAR).ownerName("Alice").build()
            );
            given(txService.getParkedVehicles(LOT_ID)).willReturn(expected);

            // Act
            List<VehicleView> result = sessionService.getParkedVehicles(LOT_ID);

            // Assert
            assertThat(result).isSameAs(expected);
            then(txService).should().getParkedVehicles(LOT_ID);
            then(redissonClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Should propagate NotFoundException from txService")
        void shouldPropagateNotFoundException() {
            // Arrange
            given(txService.getParkedVehicles(LOT_ID))
                    .willThrow(new NotFoundException("lot not found"));

            // Act & Assert
            assertThatThrownBy(() -> sessionService.getParkedVehicles(LOT_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}