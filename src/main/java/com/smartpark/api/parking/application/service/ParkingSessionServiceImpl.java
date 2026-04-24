package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.request.CheckInRequest;
import com.smartpark.api.parking.api.dto.request.CheckOutRequest;
import com.smartpark.api.parking.api.dto.response.CheckInResponse;
import com.smartpark.api.parking.api.dto.response.CheckOutResponse;
import com.smartpark.api.parking.application.contract.VehicleView;
import com.smartpark.api.parking.application.exception.DbLockTimeoutException;
import com.smartpark.api.parking.application.exception.RedisBusyException;
import com.smartpark.api.parking.application.exception.TooManyRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingSessionServiceImpl implements ParkingSessionService {
    private static final String LOCK_PREFIX = "smartpark:lot:";
    private static final int LOCK_WAIT_MS = 0;  // immediate fail if busy
    private static final int LOCK_LEASE_MS = 200;   // auto-expire after 200 ms

    private final RedissonClient redissonClient;
    private final ParkingSessionTxService txService;

    @Override
    public CheckInResponse checkIn(CheckInRequest request) {
        String lotId = request.lotId();
        String plate = request.licensePlate();

        // Tier 1: Redis distributed lock
        try {
            return checkInWithRedisLock(lotId, plate);
        } catch (RedisBusyException e) {
            log.warn("[CHECK-IN] Redis lock BUSY for lot '{}' – falling back to DB lock", lotId);
        } catch (RedisException e) {
            log.warn("[CHECK-IN] Redis UNAVAILABLE for lot '{}' – falling back to DB lock. Cause: {}",
                    lotId, e.getMessage());
        }

        // Tier 2: DB pessimistic lock
        try {
            return txService.executeCheckInWithDbLock(lotId, plate);
        } catch (DbLockTimeoutException e) {
            log.warn("[CHECK-IN] DB lock TIMEOUT for lot '{}' – returning 429", lotId);

            throw new TooManyRequestException(
                    "System is under high load for lot '" + lotId + "'. Please retry after 2 seconds."
            );
        }
        // Business exceptions propagate naturally
    }

    @Override
    public CheckOutResponse checkOut(CheckOutRequest request) {
        String lotId = request.lotId();
        String plate = request.licensePlate();

        // Tier 1: Redis distributed lock
        try {
            return checkOutWithRedisLock(lotId, plate);
        } catch (RedisBusyException e) {
            log.warn("[CHECK-OUT] Redis lock BUSY for lot '{}' – falling back to DB lock", lotId);
        } catch (RedisException e) {
            log.warn("[CHECK-OUT] Redis UNAVAILABLE for lot '{}' – falling back to DB lock. Cause: {}",
                    lotId, e.getMessage());
        }

        // Tier 2: DB pessimistic lock
        try {
            return txService.executeCheckOutWithDbLock(lotId, plate);
        } catch (DbLockTimeoutException e) {
            log.warn("[CHECK-OUT] DB lock TIMEOUT for lot '{}' – returning 429", lotId);
            throw new TooManyRequestException(
                    "System is under high load for lot '" + lotId + "'. Please retry after 2 seconds.");
        }
    }

    @Override
    public List<VehicleView> getParkedVehicles(String lotId) {
        return txService.getParkedVehicles(lotId);
    }

    /**
     * Attempts check-in using a Redis distributed lock.
     * <p>
     * Throws {@link RedisBusyException} if the lock is held by another node (tryLock returns {@code false}).
     * Throws {@link RedisException} (propagated) if Redis itself is unreachable.
     * Any business exception from {@link ParkingSessionTxService#executeCheckIn} propagates unchanged; it is NOT a Redis failure.
     */
    private CheckInResponse checkInWithRedisLock(String lotId, String plate) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lotId);
        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RedisBusyException("Redis lock busy for lot '" + lotId + "'");
            }
            try {
                // Lock held -> open transaction -> work -> commit
                return txService.executeCheckIn(lotId, plate);
            } finally {
                safeUnlock(lock, lotId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisBusyException("Interrupted while acquiring Redis lock for lot '" + lotId + "'");
        }
        // RedisException from tryLock() propagates to the caller
    }

    /**
     * Attempts check-out using a Redis distributed lock.
     */
    private CheckOutResponse checkOutWithRedisLock(String lotId, String plate) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lotId);
        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RedisBusyException("Redis lock busy for lot '" + lotId + "'");
            }
            try {
                return txService.executeCheckOut(lotId, plate);
            } finally {
                safeUnlock(lock, lotId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisBusyException("Interrupted while acquiring Redis lock for lot '" + lotId + "'");
        }
    }

    private void safeUnlock(RLock lock, String lotId) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception ex) {
            // Log but do not rethrow; the DB work has already committed.
            log.error("[LOCK] Failed to release Redis lock for lot '{}': {}", lotId, ex.getMessage());
        }
    }
}