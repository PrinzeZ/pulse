package com.pulse.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING)
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    protected Long userId;

    protected String name;
    protected String username;
    @JsonIgnore
    protected String password;

    @Column(name = "state_id")
    protected Long stateId;

    @Column(name = "district_id")
    protected Long districtId;

    @Column(name = "hospital_id")
    protected Long hospitalId;

    @Column(name = "enabled")
    protected Boolean enabled = true;

    @Column(name = "policy_version", length = 30)
    protected String policyVersion;

    @Column(name = "policy_accepted_at")
    protected LocalDateTime policyAcceptedAt;

    public User() {}

    public User(Long userId, String name, String username, String password){
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.password = password;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Long getStateId() { return stateId; }
    public void setStateId(Long stateId) { this.stateId = stateId; }
    public Long getDistrictId() { return districtId; }
    public void setDistrictId(Long districtId) { this.districtId = districtId; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled == null || enabled; }

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public LocalDateTime getPolicyAcceptedAt() { return policyAcceptedAt; }
    public void setPolicyAcceptedAt(LocalDateTime policyAcceptedAt) { this.policyAcceptedAt = policyAcceptedAt; }
}