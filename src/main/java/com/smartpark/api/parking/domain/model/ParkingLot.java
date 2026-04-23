package com.smartpark.api.parking.domain.model;

import com.smartpark.api.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "parking_lot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingLot extends BaseEntity {
    /** Client-provided id, e.g., "LOT-SM-01". */
    @Id
    @Column(name = "lot_id", length = 50, nullable = false)
    private String lotId;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "occupied_spaces", nullable = false)
    private int occupiedSpaces;
}