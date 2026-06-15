package com.garage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    @Value("${garage.pricing.hourly-rate:10.0}")
    private double hourlyRate;

    @Value("${garage.pricing.daily-max:80.0}")
    private double dailyMax;

    @Value("${garage.pricing.free-minutes:30}")
    private int freeMinutes;

    public double calculateFee(LocalDateTime entryTime, LocalDateTime exitTime) {
        Duration duration = Duration.between(entryTime, exitTime);
        long totalMinutes = duration.toMinutes();

        if (totalMinutes <= freeMinutes) {
            return 0.0;
        }

        long billableMinutes = totalMinutes - freeMinutes;
        long days = billableMinutes / (24 * 60);
        long remainingMinutes = billableMinutes % (24 * 60);

        double dailyCharge = days * dailyMax;
        double hourlyCharge = Math.ceil(remainingMinutes / 60.0) * hourlyRate;

        double total = dailyCharge + Math.min(hourlyCharge, dailyMax);

        return Math.round(total * 100.0) / 100.0;
    }

    public long getDurationMinutes(LocalDateTime entryTime, LocalDateTime exitTime) {
        return Duration.between(entryTime, exitTime).toMinutes();
    }

    public double getHourlyRate() {
        return hourlyRate;
    }

    public double getDailyMax() {
        return dailyMax;
    }

    public int getFreeMinutes() {
        return freeMinutes;
    }
}
