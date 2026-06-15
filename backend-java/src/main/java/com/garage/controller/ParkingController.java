package com.garage.controller;

import com.garage.model.*;
import com.garage.service.ParkingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parking")
@CrossOrigin(origins = "*")
public class ParkingController {

    @Autowired
    private ParkingService parkingService;

    @PostMapping("/park")
    public ApiResponse<ParkingSpot> parkCar(@RequestBody ParkRequest request) {
        return parkingService.parkCar(request);
    }

    @PostMapping("/pickup")
    public ApiResponse<PickupResponse> pickupCar(@RequestBody PickupRequest request) {
        return parkingService.pickupCar(request);
    }

    @PostMapping("/complete/{licensePlate}")
    public ApiResponse<ParkingRecord> completePickup(@PathVariable String licensePlate) {
        return parkingService.completePickup(licensePlate);
    }

    @GetMapping("/spot/{licensePlate}")
    public ApiResponse<ParkingSpot> getParkingSpot(@PathVariable String licensePlate) {
        return parkingService.getParkingSpotByPlate(licensePlate);
    }

    @GetMapping("/spots")
    public ApiResponse<List<ParkingSpot>> getAllParkingSpots() {
        return parkingService.getAllParkingSpots();
    }

    @GetMapping("/records")
    public ApiResponse<List<ParkingRecord>> getParkingRecords(
            @RequestParam(required = false) String licensePlate) {
        return parkingService.getParkingRecords(licensePlate);
    }

    @GetMapping("/available")
    public ApiResponse<Long> getAvailableSpots() {
        return parkingService.getAvailableSpotsCount();
    }
}
