package com.smartpark.api.parking.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "parking_session",
        indexes = {
                @Index(name = "idx_session_vehicle_active",
                        columnList = "license_plate, active"),
                @Index(name = "idx_session_lot_active",
                        columnList = "lot_id, active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot parkingLot;

    @Column(name = "license_plate", nullable = false)
    private String licensePlate;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Column(name = "active", nullable = false)
    private boolean active;
}