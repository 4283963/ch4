package com.garage.repository;

import com.garage.model.ParkingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingRecordRepository extends JpaRepository<ParkingRecord, Long> {

    Optional<ParkingRecord> findFirstByLicensePlateAndStatusOrderByEntryTimeDesc(String licensePlate, String status);

    List<ParkingRecord> findByLicensePlateOrderByEntryTimeDesc(String licensePlate);

    List<ParkingRecord> findTop10ByOrderByEntryTimeDesc();
}
