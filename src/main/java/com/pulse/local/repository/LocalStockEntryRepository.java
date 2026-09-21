package com.pulse.local.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pulse.local.model.LocalStockEntry;

public interface LocalStockEntryRepository extends JpaRepository<LocalStockEntry, Long> {
    List<LocalStockEntry> findByHospitalId(Long hospitalId);
    List<LocalStockEntry> findBySyncedFalse();
    List<LocalStockEntry> findByHospitalIdAndSyncedFalse(Long hospitalId);
    Optional<LocalStockEntry> findByCloudEntryId(Long cloudEntryId);
    Optional<LocalStockEntry> findByHospitalIdAndMedicineId(Long hospitalId, Long medicineId);
}
