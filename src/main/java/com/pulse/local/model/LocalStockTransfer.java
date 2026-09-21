package com.pulse.local.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "local_stock_transfers")
public class LocalStockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long localTransferId;

    @Column(name = "cloud_transfer_id")
    private Long cloudTransferId;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private Long sourceHospitalId;

    @Column(nullable = false)
    private Long destinationHospitalId;

    @Column(nullable = false)
    private Long medicineId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 30)
    private String status;

    private String createdByUsername;
    private String dispatchedByUsername;
    private String receivedByUsername;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean pendingSync = true;

    public Long getLocalTransferId() { return localTransferId; }
    public void setLocalTransferId(Long localTransferId) { this.localTransferId = localTransferId; }
    public Long getCloudTransferId() { return cloudTransferId; }
    public void setCloudTransferId(Long cloudTransferId) { this.cloudTransferId = cloudTransferId; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getSourceHospitalId() { return sourceHospitalId; }
    public void setSourceHospitalId(Long sourceHospitalId) { this.sourceHospitalId = sourceHospitalId; }
    public Long getDestinationHospitalId() { return destinationHospitalId; }
    public void setDestinationHospitalId(Long destinationHospitalId) { this.destinationHospitalId = destinationHospitalId; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }
    public String getDispatchedByUsername() { return dispatchedByUsername; }
    public void setDispatchedByUsername(String dispatchedByUsername) { this.dispatchedByUsername = dispatchedByUsername; }
    public String getReceivedByUsername() { return receivedByUsername; }
    public void setReceivedByUsername(String receivedByUsername) { this.receivedByUsername = receivedByUsername; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isPendingSync() { return pendingSync; }
    public void setPendingSync(boolean pendingSync) { this.pendingSync = pendingSync; }
}
