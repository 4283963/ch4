package com.garage.model;

import java.time.LocalDateTime;

public class PickupResponse {
    private String licensePlate;
    private Integer carrierIndex;
    private Integer targetCarrierIndex;
    private String direction;
    private Integer steps;
    private Integer estimatedTimeMs;
    private Double amount;
    private Long durationMinutes;
    private LocalDateTime parkTime;
    private String status;

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public Integer getCarrierIndex() { return carrierIndex; }
    public void setCarrierIndex(Integer carrierIndex) { this.carrierIndex = carrierIndex; }

    public Integer getTargetCarrierIndex() { return targetCarrierIndex; }
    public void setTargetCarrierIndex(Integer targetCarrierIndex) { this.targetCarrierIndex = targetCarrierIndex; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }

    public Integer getEstimatedTimeMs() { return estimatedTimeMs; }
    public void setEstimatedTimeMs(Integer estimatedTimeMs) { this.estimatedTimeMs = estimatedTimeMs; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Long durationMinutes) { this.durationMinutes = durationMinutes; }

    public LocalDateTime getParkTime() { return parkTime; }
    public void setParkTime(LocalDateTime parkTime) { this.parkTime = parkTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
