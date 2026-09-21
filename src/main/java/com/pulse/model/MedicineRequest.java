package com.pulse.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_requests")
public class MedicineRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MedicineRequestStatus status;

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

    public MedicineRequest() {}

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = MedicineRequestStatus.PENDING_DISTRICT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
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
    public MedicineRequestStatus getStatus() { return status; }
    public void setStatus(MedicineRequestStatus status) { this.status = status; }
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
}
