package com.smartpark.api.parking.infra.repository;

import com.smartpark.api.parking.domain.model.ParkingSession;
import com.smartpark.api.parking.domain.repository.ParkingSessionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaParkingSessionRepository extends JpaRepository<ParkingSession, String>, ParkingSessionRepository {
    /**
     * Returns the active session for a vehicle across all lots.
     * Used for lookups where the lot is not known (e.g., quick search by plate).
     */
    @Query("""
        SELECT ps FROM ParkingSession ps
        WHERE ps.licensePlate = :licensePlate AND ps.active = true
    """)
    Optional<ParkingSession> findActiveByLicensePlate(@Param("licensePlate") String licensePlate);

    /**
     * Returns the active session for a specific vehicle in a specific lot.
     * Used during check-out to locate the session to close.
     */
    @Query("""
        SELECT ps FROM ParkingSession ps
        WHERE ps.parkingLot.lotId = :lotId
          AND ps.licensePlate = :licensePlate
          AND ps.active = true
    """)
    Optional<ParkingSession> findActiveByLotAndLicensePlate(
            @Param("lotId") String lotId,
            @Param("licensePlate") String licensePlate);

    /**
     * Returns all active sessions for a given lot.
     */
    @Query("""
        SELECT ps FROM ParkingSession ps
        WHERE ps.parkingLot.lotId = :lotId AND ps.active = true
    """)
    List<ParkingSession> findAllActiveByLotId(@Param("lotId") String lotId);
}