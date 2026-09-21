package com.pulse.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transfers")
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long transferId;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockTransferStatus status = StockTransferStatus.CREATED;

    @Column(length = 100)
    private String createdByUsername;

    @Column(length = 100)
    private String dispatchedByUsername;

    @Column(length = 100)
    private String receivedByUsername;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = StockTransferStatus.CREATED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
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
    public StockTransferStatus getStatus() { return status; }
    public void setStatus(StockTransferStatus status) { this.status = status; }
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
}
