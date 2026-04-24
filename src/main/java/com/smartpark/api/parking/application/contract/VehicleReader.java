package com.smartpark.api.parking.application.contract;

import java.util.List;

public interface VehicleReader {
    List<VehicleView> findAllByLicensePlates(List<String> licensePlates);
    boolean exists(String licensePlate);
}