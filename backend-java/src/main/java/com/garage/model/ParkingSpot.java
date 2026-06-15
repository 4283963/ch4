package com.garage.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_spots")
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "carrier_index", unique = true, nullable = false)
    private Integer carrierIndex;

    @Column(name = "license_plate")
    private String licensePlate;

    @Column(name = "is_occupied")
    private Boolean isOccupied = false;

    @Column(name = "park_time")
    private LocalDateTime parkTime;

    @Column(name = "car_weight_kg")
    private Double carWeightKg;

    @Column(name = "car_model")
    private String carModel;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "owner_phone")
    private String ownerPhone;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getCarrierIndex() { return carrierIndex; }
    public void setCarrierIndex(Integer carrierIndex) { this.carrierIndex = carrierIndex; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public Boolean getIsOccupied() { return isOccupied; }
    public void setIsOccupied(Boolean isOccupied) { this.isOccupied = isOccupied; }

    public LocalDateTime getParkTime() { return parkTime; }
    public void setParkTime(LocalDateTime parkTime) { this.parkTime = parkTime; }

    public Double getCarWeightKg() { return carWeightKg; }
    public void setCarWeightKg(Double carWeightKg) { this.carWeightKg = carWeightKg; }

    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
}
