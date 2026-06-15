package com.garage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GarageGatewayService {

    private static final Logger log = LoggerFactory.getLogger(GarageGatewayService.class);

    private final AtomicBoolean isRotating = new AtomicBoolean(false);
    private final AtomicInteger currentGroundIndex = new AtomicInteger(0);
    private final AtomicInteger targetIndex = new AtomicInteger(0);

    public boolean rotateToCarrier(int carrierIndex, ParkingService.RotateDirection direction, int steps) {
        log.info("Sending rotate command to gateway: carrierIndex={}, direction={}, steps={}",
                carrierIndex, direction, steps);

        if (isRotating.get()) {
            log.warn("Garage is already rotating");
            return false;
        }

        isRotating.set(true);
        targetIndex.set(carrierIndex);

        new Thread(() -> {
            try {
                int delay = steps * 500;
                log.info("Rotation started, estimated time: {}ms", delay);
                Thread.sleep(Math.min(delay, 5000));

                currentGroundIndex.set(carrierIndex);
                log.info("Rotation completed, ground carrier index: {}", carrierIndex);
            } catch (InterruptedException e) {
                log.error("Rotation interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                isRotating.set(false);
            }
        }).start();

        return true;
    }

    public boolean isRotating() {
        return isRotating.get();
    }

    public int getCurrentGroundIndex() {
        return currentGroundIndex.get();
    }

    public int getTargetIndex() {
        return targetIndex.get();
    }

    public double getWeight(int carrierIndex) {
        double[] weights = {1250.5, 980.3, 1500.0, 850.2, 1100.7, 1350.0, 0.0, 920.5};
        if (carrierIndex >= 0 && carrierIndex < weights.length) {
            return weights[carrierIndex];
        }
        return 0.0;
    }

    public boolean emergencyStop(String reason) {
        log.warn("Emergency stop activated: {}", reason);
        isRotating.set(false);
        return true;
    }
}
