package com.garage.repository;

import com.garage.model.ParkingSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    Optional<ParkingSpot> findByCarrierIndex(Integer carrierIndex);

    Optional<ParkingSpot> findByLicensePlateAndIsOccupiedTrue(String licensePlate);

    List<ParkingSpot> findByIsOccupiedFalse();

    List<ParkingSpot> findByIsOccupiedTrue();

    long countByIsOccupiedFalse();
}
