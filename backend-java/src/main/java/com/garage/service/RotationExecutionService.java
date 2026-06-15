package com.garage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class RotationExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RotationExecutionService.class);

    private final ConcurrentHashMap<String, Boolean> activeRotations = new ConcurrentHashMap<>();

    @Async
    public void executeRotation(String requestId, int carrierIndex, ParkingService.RotateDirection direction,
                                int steps, Runnable onComplete) {
        if (activeRotations.putIfAbsent(requestId, true) != null) {
            log.warn("Duplicate rotation execution attempt for request={}", requestId);
            return;
        }

        try {
            int delay = steps * 500;
            log.info("Rotation started for request={}, estimated time: {}ms", requestId, delay);

            Thread.sleep(Math.min(delay, 5000));

            log.info("Rotation physically completed for request={}", requestId);
            onComplete.run();

        } catch (InterruptedException e) {
            log.error("Rotation interrupted for request={}", requestId, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Rotation failed for request={}", requestId, e);
            onComplete.run();
        } finally {
            activeRotations.remove(requestId);
        }
    }

    public boolean isRotationActive(String requestId) {
        return activeRotations.containsKey(requestId);
    }
}
