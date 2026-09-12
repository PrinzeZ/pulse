package com.pulse.model;

import java.time.LocalDateTime;
// a new object that hasn't been saved to the database has no ID yet. With Long, the ID field can be null meaning "not assigned yet."
//  With long, it would silently show 0, which looks like a real ID and causes bugs.
//  When Sanu adds @GeneratedValue (auto-increment IDs from MySQL), it needs Long.
public class Alert {
    private Long alertId;
    private Long hospitalId;
    private Long medId;
    
    private String message;
    private LocalDateTime createdAt;
    private boolean resolved;

    public Alert() {}

    public Alert(Long hospitalId, Long medId, String message) {
        this.hospitalId = hospitalId;
        this.medId = medId;
        this.message = message;
        this.createdAt = LocalDateTime.now();
        this.resolved = false;
    }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }
    
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
   
    public Long getmedId() { return medId; }
    public void setmedId(Long medId) { this.medId = medId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}