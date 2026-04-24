package com.smartpark.api.parking.application.service;

import com.smartpark.api.parking.api.dto.request.CheckInRequest;
import com.smartpark.api.parking.api.dto.response.CheckInResponse;
import com.smartpark.api.parking.api.dto.request.CheckOutRequest;
import com.smartpark.api.parking.api.dto.response.CheckOutResponse;
import com.smartpark.api.parking.application.contract.VehicleView;
import com.smartpark.api.parking.application.exception.DbLockTimeoutException;
import com.smartpark.api.parking.application.exception.RedisBusyException;
import com.smartpark.api.parking.application.exception.TooManyRequestException;
import com.smartpark.api.shared.exception.GlobalExceptionHandler;

import java.util.List;

/**
 * Orchestrates the three-tier concurrency fallback chain for check-in and check-out.
 * <h3>Locking order</h3>
 * <pre>
 *  acquire lock -> start transaction -> DB work -> commit -> release lock
 * </pre>
 * <h3>Fallback tiers</h3>
 * <ol>
 *   <li><b>Redis distributed lock</b> – {@code tryLock(waitTime=0, leaseTime=200ms)}.
 *       Zero wait: if the lock is held by another thread, return immediately with {@code false}
 *       -> throw {@link RedisBusyException} and fall to tier 2.
 *   </li>
 *   <li>
 *       <b>DB pessimistic lock</b> – {@code SELECT FOR UPDATE} with 500 ms timeout.
 *       If timeout -> throw {@link DbLockTimeoutException} and fall to tier 3.
 *   </li>
 *   <li>
 *       <b>HTTP 429</b> – {@link TooManyRequestException} is mapped to {@code 429 Too Many Requests} with
 *       {@code Retry-After: 2} by {@link GlobalExceptionHandler}.
 *   </li>
 * </ol>
 * <p>
 * This class is intentionally <strong>not</strong> {@code @Transactional} — transactions are opened inside
 * {@link ParkingSessionTxService} methods, <em>after</em> the lock is held.
 */
public interface ParkingSessionService {
    CheckInResponse checkIn(CheckInRequest request);
    CheckOutResponse checkOut(CheckOutRequest request);
    List<VehicleView> getParkedVehicles(String lotId);
}