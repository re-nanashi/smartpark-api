package com.smartpark.api.vehicle.domain.model;

import com.smartpark.api.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle extends BaseEntity {
    /** Client-provided plate number, e.g., "NBA-0420".*/
    @Id
    @Column(name = "license_plate", length = 20, nullable = false)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "owner_name", length = 100, nullable = false)
    private String ownerName;
}