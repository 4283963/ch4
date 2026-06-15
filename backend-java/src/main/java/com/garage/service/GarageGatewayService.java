package com.garage.service;

import com.garage.model.GroundScaleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GarageGatewayService {

    private static final Logger log = LoggerFactory.getLogger(GarageGatewayService.class);

    private static final double MAX_WEIGHT_KG = 2500.0;

    private final AtomicInteger currentGroundIndex = new AtomicInteger(0);
    private final AtomicInteger targetIndex = new AtomicInteger(0);

    private volatile double simulatedGroundWeightKg = 1250.0;

    @Autowired
    private GarageRotationLockService lockService;

    @Autowired
    private RotationExecutionService rotationExecutionService;

    public GroundScaleResponse readGroundScale() {
        GroundScaleResponse resp = new GroundScaleResponse();
        resp.setWeightKg(simulatedGroundWeightKg);
        resp.setMaxWeightKg(MAX_WEIGHT_KG);
        resp.setOverload(simulatedGroundWeightKg > MAX_WEIGHT_KG);
        log.info("Read ground scale: weight=%.2fkg, overload=%s, max=%.2fkg".formatted(
                simulatedGroundWeightKg, resp.isOverload(), MAX_WEIGHT_KG));
        return resp;
    }

    public void setGroundScaleOverride(double weightKg) {
        simulatedGroundWeightKg = weightKg;
        log.info("Ground scale override set to %.2fkg".formatted(weightKg));
    }

    public boolean checkWeightSafety() {
        GroundScaleResponse scale = readGroundScale();
        if (scale.isOverload()) {
            log.error("WEIGHT OVERLOAD DETECTED: %.2fkg > %.2fkg, BLOCKING rotation!".formatted(
                    scale.getWeightKg(), scale.getMaxWeightKg()));
            return false;
        }
        return true;
    }

    public boolean rotateToCarrier(String requestId, int carrierIndex, ParkingService.RotateDirection direction, int steps) {
        if (!checkWeightSafety()) {
            log.warn("Rotation blocked for request={} due to weight overload", requestId);
            return false;
        }

        log.info("Executing rotation for request={}, carrierIndex={}, direction={}, steps={}",
                requestId, carrierIndex, direction, steps);

        targetIndex.set(carrierIndex);

        rotationExecutionService.executeRotation(requestId, carrierIndex, direction, steps, () -> {
            currentGroundIndex.set(carrierIndex);
            log.info("Rotation completed for request={}, ground carrier index: {}", requestId, carrierIndex);

            lockService.releaseLock(requestId);

            String nextRequestId = lockService.pollQueue();
            if (nextRequestId != null) {
                log.info("Dequeued next request={} for execution", nextRequestId);
            }
        });

        return true;
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
        lockService.forceReleaseAll();
        return true;
    }
}
