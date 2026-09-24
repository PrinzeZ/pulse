package com.pulse.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "medicine_request_action_logs")
public class MedicineRequestActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long actionLogId;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId = UUID.randomUUID().toString();
    private Long requestId;
    @Column(nullable = false) private Long hospitalId;
    @Column(nullable = false) private Long districtId;
    private Long stateId;
    @Column(nullable = false) private Long medicineId;
    @Column(nullable = false, length = 40) private String actorRole;
    @Column(nullable = false, length = 100) private String actorUsername;
    @Column(nullable = false, length = 32) private String tier;
    @Column(nullable = false, length = 40) private String action;
    @Column(length = 40) private String fromStatus;
    @Column(length = 40) private String toStatus;
    @Column(nullable = false) private int requestedQuantity;
    @Column(nullable = false) private int fulfilledQuantity;
    @Column(length = 500) private String note;
    @Column(nullable = false) private LocalDateTime occurredAt;
    @Column(length = 64) private String previousHash;
    @Column(nullable = false, length = 64) private String hash;

    public Long getActionLogId() { return actionLogId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
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
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(int requestedQuantity) { this.requestedQuantity = requestedQuantity; }
    public int getFulfilledQuantity() { return fulfilledQuantity; }
    public void setFulfilledQuantity(int fulfilledQuantity) { this.fulfilledQuantity = fulfilledQuantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
