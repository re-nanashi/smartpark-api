package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.response.CheckInResponse;
import com.smartpark.api.parking.api.dto.response.CheckOutResponse;
import com.smartpark.api.parking.application.contract.VehicleView;
import com.smartpark.api.parking.application.exception.DbLockTimeoutException;

import java.util.List;

/**
 * Transactional component that performs the actual DB mutations for check-in/out.
 * <p>
 * <strong>Separation of concerns:</strong> This class is {@code @Transactional}. The Redis lock is acquired by {@link ParkingSessionService}
 * <em>before</em> calling these methods, so the transaction is started <em>after</em> the lock is held:
 * <pre>
 *     acquire lock -> start transaction -> DB work -> commit -> release lock
 * </pre>
 * <p>
 * Two variants of each operation exist:
 * <ul>
 *   <li>{@code execute*()} — used when a Redis lock is already held; plain {@code findById}.</li>
 *   <li>{@code executeWithDbLock*()} — fallback; uses {@code SELECT FOR UPDATE} for mutual exclusion when Redis is
 *       unavailable or busy.</li>
 * </ul>
 */
public interface ParkingSessionTxService {
    /**
     * Called when a Redis distributed lock is already held.
     * Uses a normal {@code findById}; no additional DB lock required.
     */
    CheckInResponse executeCheckIn(String lotId, String licensePlate);
    CheckOutResponse executeCheckOut(String lotId, String licensePlate);

    /**
     * Fallback path: acquires a pessimistic DB lock ({@code SELECT FOR UPDATE}) before proceeding.
     * <p>
     * If the lock wait exceeds ~500 ms (set via H2 {@code LOCK_TIMEOUT} and the {@code jakarta.persistence.lock.timeout} hint),
     * the underlying exception is caught and re-thrown as {@link DbLockTimeoutException} so the caller can promote to HTTP 429.
     */
    CheckInResponse executeCheckInWithDbLock(String lotId, String licensePlate);
    CheckOutResponse executeCheckOutWithDbLock(String lotId, String licensePlate);

    List<VehicleView> getParkedVehicles(String lotId);
}