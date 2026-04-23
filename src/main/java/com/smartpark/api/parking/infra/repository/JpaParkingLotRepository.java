package com.smartpark.api.parking.infra.repository;

import com.smartpark.api.parking.domain.model.ParkingLot;
import com.smartpark.api.parking.domain.repository.ParkingLotRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaParkingLotRepository extends JpaRepository<ParkingLot, String>, ParkingLotRepository {
    /**
     * Acquires a {@code SELECT FOR UPDATE} (pessimistic-write) lock on the row.
     * <p>
     * Used as the second tier of the fallback chain when Redis is unavailable or busy.
     * The {@code jakarta.persistence.lock.timeout} hint is combined with the H2 {@code LOCK_TIMEOUT=500} JDBC-URL
     * setting so the lock attempt aborts after ~500 ms, allowing the service to promote to HTTP 429.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "500")
    })
    @Query("SELECT pl FROM ParkingLot pl WHERE pl.lotId = :lotId")
    Optional<ParkingLot> findByIdWithLock(@Param("lotId") String lotId);
}