package com.pulse.repository;

import com.pulse.model.StockEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockEntryRepository extends JpaRepository<StockEntry, Long> {
    List<StockEntry> findByHospitalId(Long hospitalId);
    List<StockEntry> findByQuantityLessThan(int threshold);
}