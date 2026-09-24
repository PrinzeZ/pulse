package com.pulse.local.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "local_medicine_request_action_logs")
public class LocalMedicineRequestActionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long actionLogId;
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId = UUID.randomUUID().toString();
    private Long cloudRequestId;
    private Long localRequestId;
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
    @Column(nullable = false) private boolean pendingSync = true;

    public Long getActionLogId(){return actionLogId;} public String getEventId(){return eventId;} public void setEventId(String v){eventId=v;}
    public Long getCloudRequestId(){return cloudRequestId;} public void setCloudRequestId(Long v){cloudRequestId=v;}
    public Long getLocalRequestId(){return localRequestId;} public void setLocalRequestId(Long v){localRequestId=v;}
    public Long getHospitalId(){return hospitalId;} public void setHospitalId(Long v){hospitalId=v;} public Long getDistrictId(){return districtId;} public void setDistrictId(Long v){districtId=v;}
    public Long getStateId(){return stateId;} public void setStateId(Long v){stateId=v;} public Long getMedicineId(){return medicineId;} public void setMedicineId(Long v){medicineId=v;}
    public String getActorRole(){return actorRole;} public void setActorRole(String v){actorRole=v;} public String getActorUsername(){return actorUsername;} public void setActorUsername(String v){actorUsername=v;}
    public String getTier(){return tier;} public void setTier(String v){tier=v;} public String getAction(){return action;} public void setAction(String v){action=v;}
    public String getFromStatus(){return fromStatus;} public void setFromStatus(String v){fromStatus=v;} public String getToStatus(){return toStatus;} public void setToStatus(String v){toStatus=v;}
    public int getRequestedQuantity(){return requestedQuantity;} public void setRequestedQuantity(int v){requestedQuantity=v;} public int getFulfilledQuantity(){return fulfilledQuantity;} public void setFulfilledQuantity(int v){fulfilledQuantity=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;} public LocalDateTime getOccurredAt(){return occurredAt;} public void setOccurredAt(LocalDateTime v){occurredAt=v;}
    public String getPreviousHash(){return previousHash;} public void setPreviousHash(String v){previousHash=v;} public String getHash(){return hash;} public void setHash(String v){hash=v;}
    public boolean isPendingSync(){return pendingSync;} public void setPendingSync(boolean v){pendingSync=v;}
}
