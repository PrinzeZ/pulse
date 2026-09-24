package com.pulse.repository;

import com.pulse.model.MedicineRequestAuditArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MedicineRequestAuditArchiveRepository extends JpaRepository<MedicineRequestAuditArchive, Long> {
    List<MedicineRequestAuditArchive> findByHospitalIdOrderByPeriodStartDesc(Long hospitalId);
    List<MedicineRequestAuditArchive> findByHospitalIdAndGranularityOrderByPeriodStartAsc(Long hospitalId, String granularity);
    Optional<MedicineRequestAuditArchive> findByHospitalIdAndGranularityAndPeriodStart(Long hospitalId, String granularity, LocalDate periodStart);
    List<MedicineRequestAuditArchive> findByPeriodEndBefore(LocalDate cutoff);
}
