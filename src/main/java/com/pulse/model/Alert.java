package com.pulse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "hospital_id")
    private Long hospitalId;

    @Column(name = "medicine_id")
    private Long medId;

    private String message;

    @Column(name = "created_at")
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