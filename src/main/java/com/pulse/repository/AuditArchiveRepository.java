package com.pulse.repository;

import com.pulse.model.AuditArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AuditArchiveRepository extends JpaRepository<AuditArchive, Long> {
    List<AuditArchive> findByHospitalIdOrderByPeriodStartDesc(Long hospitalId);
    List<AuditArchive> findByHospitalIdAndGranularityOrderByPeriodStartAsc(Long hospitalId, String granularity);
    Optional<AuditArchive> findByHospitalIdAndGranularityAndPeriodStart(Long hospitalId, String granularity, LocalDate periodStart);
    List<AuditArchive> findByPeriodEndBefore(LocalDate cutoff);
}
