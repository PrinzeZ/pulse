package com.pulse.local.repository;

import com.pulse.local.model.LocalMedicineRequestAuditArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LocalMedicineRequestAuditArchiveRepository extends JpaRepository<LocalMedicineRequestAuditArchive, Long> {
    List<LocalMedicineRequestAuditArchive> findByHospitalIdOrderByPeriodStartDesc(Long hospitalId);
    List<LocalMedicineRequestAuditArchive> findByHospitalIdAndGranularityOrderByPeriodStartAsc(Long hospitalId, String granularity);
    Optional<LocalMedicineRequestAuditArchive> findByHospitalIdAndGranularityAndPeriodStart(Long hospitalId, String granularity, LocalDate periodStart);
}
