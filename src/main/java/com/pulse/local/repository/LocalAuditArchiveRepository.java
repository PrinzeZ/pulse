package com.pulse.local.repository;

import com.pulse.local.model.LocalAuditArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LocalAuditArchiveRepository extends JpaRepository<LocalAuditArchive, Long> {
    List<LocalAuditArchive> findByHospitalIdOrderByPeriodStartDesc(Long hospitalId);
    List<LocalAuditArchive> findByHospitalIdAndGranularityOrderByPeriodStartAsc(Long hospitalId, String granularity);
    Optional<LocalAuditArchive> findByHospitalIdAndGranularityAndPeriodStart(Long hospitalId, String granularity, LocalDate periodStart);
}
