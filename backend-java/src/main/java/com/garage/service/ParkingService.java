package com.garage.service;

import com.garage.model.*;
import com.garage.repository.ParkingRecordRepository;
import com.garage.repository.ParkingSpotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ParkingService {

    private static final Logger log = LoggerFactory.getLogger(ParkingService.class);

    @Autowired
    private ParkingSpotRepository parkingSpotRepository;

    @Autowired
    private ParkingRecordRepository parkingRecordRepository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private GarageGatewayService garageGatewayService;

    @Autowired
    private GarageRotationLockService lockService;

    @Value("${garage.total-carriers:8}")
    private int totalCarriers;

    private final ConcurrentHashMap<String, PickupResponse> pendingPickups = new ConcurrentHashMap<>();

    @Transactional
    public ApiResponse<ParkingSpot> parkCar(ParkRequest request) {
        log.info("Parking car: {}", request.getLicensePlate());

        GarageRotationLockService.LockResult lockResult = lockService.acquireLockOrEnqueue(request.getLicensePlate());
        if (!lockResult.isAcquired()) {
            return ApiResponse.error(409, "车库正在调车中，请等待后重试。当前排队位置: " + lockResult.getQueuePosition());
        }

        try {
            Optional<ParkingSpot> existingSpot = parkingSpotRepository.findByLicensePlateAndIsOccupiedTrue(request.getLicensePlate());
            if (existingSpot.isPresent()) {
                return ApiResponse.error(400, "该车牌号已存在停车记录");
            }

            List<ParkingSpot> emptySpots = parkingSpotRepository.findByIsOccupiedFalse();
            if (emptySpots.isEmpty()) {
                return ApiResponse.error(503, "车库已满，没有空闲车位");
            }

            ParkingSpot spot = emptySpots.get(0);

            if (request.getCarWeightKg() != null && request.getCarWeightKg() > 2000.0) {
                return ApiResponse.error(400, "车辆超重，最大限重2000kg");
            }

            spot.setLicensePlate(request.getLicensePlate());
            spot.setIsOccupied(true);
            spot.setParkTime(LocalDateTime.now());
            spot.setCarWeightKg(request.getCarWeightKg() != null ? request.getCarWeightKg() : 1000.0);
            spot.setCarModel(request.getCarModel());
            spot.setOwnerName(request.getOwnerName());
            spot.setOwnerPhone(request.getOwnerPhone());

            parkingSpotRepository.save(spot);

            ParkingRecord record = new ParkingRecord();
            record.setLicensePlate(request.getLicensePlate());
            record.setCarrierIndex(spot.getCarrierIndex());
            record.setEntryTime(LocalDateTime.now());
            record.setStatus("PARKED");
            record.setCarWeightKg(spot.getCarWeightKg());
            parkingRecordRepository.save(record);

            log.info("Car parked at carrier index: {}", spot.getCarrierIndex());

            return ApiResponse.success(spot);
        } finally {
            lockService.releaseLock(lockResult.getRequestId());

            String nextRequestId = lockService.pollQueue();
            if (nextRequestId != null) {
                log.info("Dequeued next request={} after park completion", nextRequestId);
            }
        }
    }

    @Transactional
    public ApiResponse<PickupResponse> pickupCar(PickupRequest request) {
        log.info("Picking up car: {}", request.getLicensePlate());

        Optional<ParkingSpot> spotOpt = parkingSpotRepository.findByLicensePlateAndIsOccupiedTrue(request.getLicensePlate());
        if (spotOpt.isEmpty()) {
            return ApiResponse.error(404, "未找到该车辆的停车记录");
        }

        ParkingSpot spot = spotOpt.get();
        LocalDateTime now = LocalDateTime.now();
        double amount = pricingService.calculateFee(spot.getParkTime(), now);
        long durationMinutes = pricingService.getDurationMinutes(spot.getParkTime(), now);

        CarrierRotationInfo rotationInfo = calculateRotation(spot.getCarrierIndex());

        GarageRotationLockService.LockResult lockResult = lockService.acquireLockOrEnqueue(request.getLicensePlate());

        PickupResponse response = new PickupResponse();
        response.setLicensePlate(spot.getLicensePlate());
        response.setCarrierIndex(spot.getCarrierIndex());
        response.setTargetCarrierIndex(0);
        response.setDirection(rotationInfo.getDirection().name());
        response.setSteps(rotationInfo.getSteps());
        response.setEstimatedTimeMs(rotationInfo.getEstimatedTimeMs());
        response.setAmount(amount);
        response.setDurationMinutes(durationMinutes);
        response.setParkTime(spot.getParkTime());

        if (!lockResult.isAcquired()) {
            response.setStatus("QUEUED");
            response.setRequestId(lockResult.getRequestId());
            response.setQueuePosition(lockResult.getQueuePosition());
            response.setCurrentHolder(lockResult.getCurrentHolder());

            log.info("Pickup queued for plate={}, requestId={}, position={}",
                    request.getLicensePlate(), lockResult.getRequestId(), lockResult.getQueuePosition());

            pendingPickups.put(lockResult.getRequestId(), response);
            return ApiResponse.success(response);
        }

        response.setStatus("ROTATING");
        response.setRequestId(lockResult.getRequestId());

        garageGatewayService.rotateToCarrier(
                lockResult.getRequestId(),
                spot.getCarrierIndex(),
                rotationInfo.getDirection(),
                rotationInfo.getSteps()
        );

        log.info("Pickup rotation started for plate={}, requestId={}",
                request.getLicensePlate(), lockResult.getRequestId());

        return ApiResponse.success(response);
    }

    @Transactional
    public ApiResponse<ParkingRecord> completePickup(String licensePlate) {
        log.info("Completing pickup for: {}", licensePlate);

        Optional<ParkingSpot> spotOpt = parkingSpotRepository.findByLicensePlateAndIsOccupiedTrue(licensePlate);
        if (spotOpt.isEmpty()) {
            return ApiResponse.error(404, "未找到该车辆");
        }

        ParkingSpot spot = spotOpt.get();
        LocalDateTime now = LocalDateTime.now();
        double amount = pricingService.calculateFee(spot.getParkTime(), now);
        long durationMinutes = pricingService.getDurationMinutes(spot.getParkTime(), now);

        spot.setIsOccupied(false);
        spot.setLicensePlate(null);
        spot.setParkTime(null);
        spot.setCarWeightKg(null);
        spot.setCarModel(null);
        spot.setOwnerName(null);
        spot.setOwnerPhone(null);
        parkingSpotRepository.save(spot);

        Optional<ParkingRecord> recordOpt = parkingRecordRepository
                .findFirstByLicensePlateAndStatusOrderByEntryTimeDesc(licensePlate, "PARKED");

        if (recordOpt.isPresent()) {
            ParkingRecord record = recordOpt.get();
            record.setExitTime(now);
            record.setDurationMinutes(durationMinutes);
            record.setAmount(amount);
            record.setStatus("COMPLETED");
            record.setPaymentMethod("ONLINE");
            parkingRecordRepository.save(record);
            return ApiResponse.success(record);
        }

        return ApiResponse.error(500, "停车记录更新失败");
    }

    public ApiResponse<QueueStatusResponse> getQueueStatus(String requestId) {
        QueueStatusResponse status = new QueueStatusResponse();
        status.setRequestId(requestId);
        status.setQueueSize(lockService.getQueueSize());
        status.setLocked(lockService.isLocked());
        status.setCurrentHolder(lockService.getCurrentHolder());

        if (lockService.isLocked()) {
            int position = lockService.getQueuePosition(requestId);
            status.setQueuePosition(position);
            if (position > 0) {
                status.setStatus("QUEUED");
            } else {
                PickupResponse pending = pendingPickups.get(requestId);
                if (pending != null && "ROTATING".equals(pending.getStatus())) {
                    status.setStatus("ROTATING");
                } else {
                    status.setStatus("WAITING");
                }
            }
        } else {
            status.setStatus("IDLE");
            status.setQueuePosition(0);
        }

        return ApiResponse.success(status);
    }

    public ApiResponse<String> forceReleaseLock() {
        lockService.forceReleaseAll();
        return ApiResponse.success("All locks released and queue cleared");
    }

    private CarrierRotationInfo calculateRotation(int targetCarrierIndex) {
        int groundIndex = 0;
        int stepsClockwise = (targetCarrierIndex - groundIndex + totalCarriers) % totalCarriers;
        int stepsCounterClockwise = (groundIndex - targetCarrierIndex + totalCarriers) % totalCarriers;

        CarrierRotationInfo info = new CarrierRotationInfo();
        if (stepsClockwise <= stepsCounterClockwise) {
            info.setDirection(RotateDirection.CLOCKWISE);
            info.setSteps(stepsClockwise);
        } else {
            info.setDirection(RotateDirection.COUNTER_CLOCKWISE);
            info.setSteps(stepsCounterClockwise);
        }
        info.setEstimatedTimeMs(info.getSteps() * 2000);

        return info;
    }

    public enum RotateDirection {
        CLOCKWISE,
        COUNTER_CLOCKWISE
    }

    private static class CarrierRotationInfo {
        private RotateDirection direction;
        private int steps;
        private int estimatedTimeMs;

        public RotateDirection getDirection() { return direction; }
        public void setDirection(RotateDirection d) { this.direction = d; }
        public int getSteps() { return steps; }
        public void setSteps(int s) { this.steps = s; }
        public int getEstimatedTimeMs() { return estimatedTimeMs; }
        public void setEstimatedTimeMs(int t) { this.estimatedTimeMs = t; }
    }

    public ApiResponse<ParkingSpot> getParkingSpotByPlate(String licensePlate) {
        Optional<ParkingSpot> spot = parkingSpotRepository.findByLicensePlateAndIsOccupiedTrue(licensePlate);
        return spot.map(ApiResponse::success).orElseGet(() -> ApiResponse.error(404, "未找到该车辆"));
    }

    public ApiResponse<List<ParkingSpot>> getAllParkingSpots() {
        return ApiResponse.success(parkingSpotRepository.findAll());
    }

    public ApiResponse<List<ParkingRecord>> getParkingRecords(String licensePlate) {
        if (licensePlate != null && !licensePlate.isEmpty()) {
            return ApiResponse.success(parkingRecordRepository.findByLicensePlateOrderByEntryTimeDesc(licensePlate));
        }
        return ApiResponse.success(parkingRecordRepository.findTop10ByOrderByEntryTimeDesc());
    }

    public ApiResponse<Long> getAvailableSpotsCount() {
        return ApiResponse.success(parkingSpotRepository.countByIsOccupiedFalse());
    }
}
