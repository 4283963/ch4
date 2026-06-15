package com.garage.config;

import com.garage.model.ParkingSpot;
import com.garage.repository.ParkingSpotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private ParkingSpotRepository parkingSpotRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing parking spots...");

        long count = parkingSpotRepository.count();
        if (count > 0) {
            log.info("Parking spots already initialized, count: {}", count);
            return;
        }

        int totalCarriers = 8;
        for (int i = 0; i < totalCarriers; i++) {
            ParkingSpot spot = new ParkingSpot();
            spot.setCarrierIndex(i);
            spot.setIsOccupied(false);
            parkingSpotRepository.save(spot);
        }

        ParkingSpot spot0 = parkingSpotRepository.findByCarrierIndex(0).orElseThrow();
        spot0.setLicensePlate("京A12345");
        spot0.setIsOccupied(true);
        spot0.setParkTime(LocalDateTime.now().minusHours(3));
        spot0.setCarWeightKg(1250.5);
        spot0.setCarModel("特斯拉 Model 3");
        spot0.setOwnerName("张三");
        spot0.setOwnerPhone("13800138001");
        parkingSpotRepository.save(spot0);

        ParkingSpot spot2 = parkingSpotRepository.findByCarrierIndex(2).orElseThrow();
        spot2.setLicensePlate("沪B67890");
        spot2.setIsOccupied(true);
        spot2.setParkTime(LocalDateTime.now().minusHours(5).minusMinutes(30));
        spot2.setCarWeightKg(980.3);
        spot2.setCarModel("大众朗逸");
        spot2.setOwnerName("李四");
        spot2.setOwnerPhone("13900139002");
        parkingSpotRepository.save(spot2);

        ParkingSpot spot4 = parkingSpotRepository.findByCarrierIndex(4).orElseThrow();
        spot4.setLicensePlate("粤C11111");
        spot4.setIsOccupied(true);
        spot4.setParkTime(LocalDateTime.now().minusMinutes(45));
        spot4.setCarWeightKg(1500.0);
        spot4.setCarModel("奔驰 E300");
        spot4.setOwnerName("王五");
        spot4.setOwnerPhone("13700137003");
        parkingSpotRepository.save(spot4);

        log.info("Parking spots initialized successfully, total: {}", totalCarriers);
    }
}
