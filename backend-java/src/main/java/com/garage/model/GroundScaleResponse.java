package com.garage.model;

public class GroundScaleResponse {
    private double weightKg;
    private boolean overload;
    private double maxWeightKg;

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public boolean isOverload() { return overload; }
    public void setOverload(boolean overload) { this.overload = overload; }

    public double getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(double maxWeightKg) { this.maxWeightKg = maxWeightKg; }
}
