package com.garage.model;

public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private Double overloadWeightKg;
    private Double maxWeightKg;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    public void setOverloadInfo(double weightKg, double maxKg) {
        this.overloadWeightKg = weightKg;
        this.maxWeightKg = maxKg;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public Double getOverloadWeightKg() { return overloadWeightKg; }
    public void setOverloadWeightKg(Double overloadWeightKg) { this.overloadWeightKg = overloadWeightKg; }

    public Double getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(Double maxWeightKg) { this.maxWeightKg = maxWeightKg; }
}
