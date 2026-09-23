package com.pulse.local.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "local_medicine_requests")
public class LocalMedicineRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long localRequestId;

    @Column(name = "cloud_request_id")
    private Long cloudRequestId;

    @Column(nullable = false)
    private Long hospitalId;

    @Column(nullable = false)
    private Long districtId;

    private Long stateId;

    @Column(nullable = false)
    private Long medicineId;

    @Column(nullable = false)
    private int requestedQuantity;

    @Column(nullable = false)
    private int fulfilledQuantity;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 100)
    private String requestedByUsername;

    @Column(length = 100)
    private String lastUpdatedByUsername;

    @Column(length = 500)
    private String districtNote;

    @Column(length = 500)
    private String stateNote;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean pendingSync = true;

    @Column(name = "audit_attachment_file_name", length = 255)
    private String auditAttachmentFileName;
    @Column(name = "audit_attachment_sha256", length = 64)
    private String auditAttachmentSha256;
    @Column(name = "audit_attachment_period_start")
    private java.time.LocalDate auditAttachmentPeriodStart;
    @Column(name = "audit_attachment_period_end")
    private java.time.LocalDate auditAttachmentPeriodEnd;
    @Column(name = "audit_attachment_created_at")
    private LocalDateTime auditAttachmentCreatedAt;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "audit_attachment_encrypted", columnDefinition = "blob")
    private byte[] auditAttachmentEncrypted;

    public LocalMedicineRequest() {}

    public Long getLocalRequestId() { return localRequestId; }
    public void setLocalRequestId(Long localRequestId) { this.localRequestId = localRequestId; }
    public Long getCloudRequestId() { return cloudRequestId; }
    public void setCloudRequestId(Long cloudRequestId) { this.cloudRequestId = cloudRequestId; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public Long getDistrictId() { return districtId; }
    public void setDistrictId(Long districtId) { this.districtId = districtId; }
    public Long getStateId() { return stateId; }
    public void setStateId(Long stateId) { this.stateId = stateId; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(int requestedQuantity) { this.requestedQuantity = requestedQuantity; }
    public int getFulfilledQuantity() { return fulfilledQuantity; }
    public void setFulfilledQuantity(int fulfilledQuantity) { this.fulfilledQuantity = fulfilledQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestedByUsername() { return requestedByUsername; }
    public void setRequestedByUsername(String requestedByUsername) { this.requestedByUsername = requestedByUsername; }
    public String getLastUpdatedByUsername() { return lastUpdatedByUsername; }
    public void setLastUpdatedByUsername(String lastUpdatedByUsername) { this.lastUpdatedByUsername = lastUpdatedByUsername; }
    public String getDistrictNote() { return districtNote; }
    public void setDistrictNote(String districtNote) { this.districtNote = districtNote; }
    public String getStateNote() { return stateNote; }
    public void setStateNote(String stateNote) { this.stateNote = stateNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isPendingSync() { return pendingSync; }
    public void setPendingSync(boolean pendingSync) { this.pendingSync = pendingSync; }
    public String getAuditAttachmentFileName() { return auditAttachmentFileName; }
    public void setAuditAttachmentFileName(String value) { this.auditAttachmentFileName = value; }
    public String getAuditAttachmentSha256() { return auditAttachmentSha256; }
    public void setAuditAttachmentSha256(String value) { this.auditAttachmentSha256 = value; }
    public java.time.LocalDate getAuditAttachmentPeriodStart() { return auditAttachmentPeriodStart; }
    public void setAuditAttachmentPeriodStart(java.time.LocalDate value) { this.auditAttachmentPeriodStart = value; }
    public java.time.LocalDate getAuditAttachmentPeriodEnd() { return auditAttachmentPeriodEnd; }
    public void setAuditAttachmentPeriodEnd(java.time.LocalDate value) { this.auditAttachmentPeriodEnd = value; }
    public LocalDateTime getAuditAttachmentCreatedAt() { return auditAttachmentCreatedAt; }
    public void setAuditAttachmentCreatedAt(LocalDateTime value) { this.auditAttachmentCreatedAt = value; }
    public byte[] getAuditAttachmentEncrypted() { return auditAttachmentEncrypted; }
    public void setAuditAttachmentEncrypted(byte[] value) { this.auditAttachmentEncrypted = value; }
}
