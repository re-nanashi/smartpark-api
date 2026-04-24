package com.smartpark.api.shared.config;

import com.smartpark.api.parking.domain.model.ParkingLot;
import com.smartpark.api.parking.domain.repository.ParkingLotRepository;
import com.smartpark.api.vehicle.domain.model.Vehicle;
import com.smartpark.api.vehicle.domain.model.VehicleType;
import com.smartpark.api.vehicle.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the H2 in-memory database with reference data on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final ParkingLotRepository lotRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedLots();
        seedVehicles();
        log.info("SmartPark seed data loaded successfully.");
    }

    private void seedLots() {
        List<ParkingLot> lots = List.of(
                ParkingLot.builder().lotId("LOT-AYALA-01").location("Ayala Center, Makati City").capacity(500).occupiedSpaces(0).build(),
                ParkingLot.builder().lotId("LOT-SMNORTH-01").location("SM City North EDSA, Quezon City").capacity(800).occupiedSpaces(0).build(),
                ParkingLot.builder().lotId("LOT-BGC-01").location("Bonifacio High Street, BGC, Taguig").capacity(300).occupiedSpaces(0).build(),
                ParkingLot.builder().lotId("LOT-ROBINSONS-01").location("Robinsons Galleria, Ortigas").capacity(600).occupiedSpaces(0).build(),
                ParkingLot.builder().lotId("LOT-GREENBELT-01").location("Greenbelt 5, Makati City").capacity(400).occupiedSpaces(0).build()
        );

        lots.forEach(lot -> {
            if (!lotRepository.existsById(lot.getLotId())) {
                lotRepository.save(lot);
                log.debug("  Seeded lot '{}'", lot.getLotId());
            }
        });
    }

    private void seedVehicles() {
        List<Vehicle> vehicles = List.of(
                Vehicle.builder().licensePlate("AAA-1234").vehicleType(VehicleType.CAR).ownerName("Juan dela Cruz").build(),
                Vehicle.builder().licensePlate("BBB-5678").vehicleType(VehicleType.CAR).ownerName("Maria Santos").build(),
                Vehicle.builder().licensePlate("CCC-9012").vehicleType(VehicleType.MOTORCYCLE).ownerName("Pedro Reyes").build(),
                Vehicle.builder().licensePlate("DDD-3456").vehicleType(VehicleType.TRUCK).ownerName("Jose Garcia").build(),
                Vehicle.builder().licensePlate("EEE-7890").vehicleType(VehicleType.CAR).ownerName("Ana Villanueva").build(),
                Vehicle.builder().licensePlate("FFF-1122").vehicleType(VehicleType.MOTORCYCLE).ownerName("Carlo Bautista").build(),
                Vehicle.builder().licensePlate("GGG-3344").vehicleType(VehicleType.CAR).ownerName("Rosa Mendoza").build(),
                Vehicle.builder().licensePlate("HHH-5566").vehicleType(VehicleType.TRUCK).ownerName("Luis Torres").build(),
                Vehicle.builder().licensePlate("III-7788").vehicleType(VehicleType.CAR).ownerName("Elena Ramos").build(),
                Vehicle.builder().licensePlate("JJJ-9900").vehicleType(VehicleType.MOTORCYCLE).ownerName("Miguel Cruz").build()
        );

        vehicles.forEach(v -> {
            if (!vehicleRepository.existsByLicensePlate(v.getLicensePlate())) {
                vehicleRepository.save(v);
                log.debug("  Seeded vehicle '{}'", v.getLicensePlate());
            }
        });
    }
}