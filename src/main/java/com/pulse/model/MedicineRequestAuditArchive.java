package com.pulse.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_request_audit_archives",
       uniqueConstraints = @UniqueConstraint(name = "uk_request_audit_archive_hospital_period", columnNames = {"hospital_id", "granularity", "period_start"}))
public class MedicineRequestAuditArchive {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long archiveId;
    @Column(nullable = false) private Long hospitalId;
    @Column(nullable = false, length = 16) private String granularity;
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @Column(nullable = false, length = 255) private String storageName;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "encrypted_payload", columnDefinition = "bytea", nullable = false)
    private byte[] encryptedPayload;

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
    public byte[] getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(byte[] v) { encryptedPayload = v; }
}
