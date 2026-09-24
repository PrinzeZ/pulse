package com.pulse.local.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "local_medicine_request_audit_archives")
public class LocalMedicineRequestAuditArchive {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long archiveId;
    @Column(nullable = false) private Long hospitalId;
    @Column(nullable = false, length = 16) private String granularity;
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @Column(nullable = false, length = 500) private String storageName;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(nullable = false) private LocalDateTime createdAt;

    public Long getArchiveId() { return archiveId; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long v) { hospitalId = v; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String v) { granularity = v; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate v) { periodStart = v; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate v) { periodEnd = v; }
    public String getStorageName() { return storageName; }
    public void setStorageName(String v) { storageName = v; }
    public String getSha256() { return sha256; }
    public void setSha256(String v) { sha256 = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
