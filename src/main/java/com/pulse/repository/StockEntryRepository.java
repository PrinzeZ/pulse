package com.pulse.repository;

import com.pulse.model.StockEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface StockEntryRepository extends JpaRepository<StockEntry, Long> {
    List<StockEntry> findByHospitalId(Long hospitalId);
    Optional<StockEntry> findByHospitalIdAndMedId(Long hospitalId, Long medId);
    List<StockEntry> findByQuantityLessThan(int threshold);

    @Query(value = "SELECT h.name AS hospital_name, h.district, m.name AS medicine_name, s.quantity, m.threshold " +
                   "FROM stock_entries s " +
                   "JOIN hospitals h ON s.hospital_id = h.hospital_id " +
                   "JOIN medicines m ON s.medicine_id = m.medicine_id " +
                   "WHERE m.name ILIKE ('%' || :query || '%') OR h.name ILIKE ('%' || :query || '%')", 
           nativeQuery = true)
    List<Object[]> searchPublicStock(@Param("query") String query);
}