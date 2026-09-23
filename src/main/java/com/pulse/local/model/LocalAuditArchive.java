package com.pulse.local.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "local_audit_archives", uniqueConstraints = @UniqueConstraint(
        name = "ux_local_audit_archive_period",
        columnNames = {"hospital_id", "granularity", "period_start"}))
public class LocalAuditArchive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long archiveId;
    @Column(nullable = false)
    private Long hospitalId;
    @Column(nullable = false, length = 12)
    private String granularity;
    @Column(nullable = false)
    private LocalDate periodStart;
    @Column(nullable = false)
    private LocalDate periodEnd;
    @Column(nullable = false, length = 255)
    private String storageName;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getArchiveId() { return archiveId; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public String getStorageName() { return storageName; }
    public void setStorageName(String storageName) { this.storageName = storageName; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
