package com.garage.model;

public class ParkRequest {
    private String licensePlate;
    private String carModel;
    private String ownerName;
    private String ownerPhone;
    private Double carWeightKg;

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }

    public Double getCarWeightKg() { return carWeightKg; }
    public void setCarWeightKg(Double carWeightKg) { this.carWeightKg = carWeightKg; }
}
