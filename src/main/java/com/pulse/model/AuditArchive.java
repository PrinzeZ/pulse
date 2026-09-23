package com.pulse.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_archives", uniqueConstraints = @UniqueConstraint(
        name = "ux_audit_archive_period",
        columnNames = {"hospital_id", "granularity", "period_start"}))
public class AuditArchive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "archive_id")
    private Long archiveId;
    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;
    @Column(nullable = false, length = 12)
    private String granularity;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(name = "storage_name", nullable = false, length = 255)
    private String storageName;
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "encrypted_payload", nullable = false)
    private byte[] encryptedPayload;

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
    public byte[] getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(byte[] encryptedPayload) { this.encryptedPayload = encryptedPayload; }
}
