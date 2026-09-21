package com.pulse.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hospital_registrations")
public class HospitalRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registration_id")
    private Long registrationId;

    @Column(nullable = false, length = 120)
    private String hospitalName;

    @Column(nullable = false, length = 180)
    private String hospitalEmail;

    @Column(nullable = false, length = 120)
    private String adminName;

    @Column(nullable = false, length = 80)
    private String adminUsername;

    @Column(nullable = false)
    private Long stateId;

    @Column(nullable = false)
    private Long districtId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HospitalRegistrationStatus status = HospitalRegistrationStatus.PENDING_EMAIL;

    @Column(nullable = false, unique = true, length = 64)
    private String verificationToken;

    @Column(length = 100)
    private String verificationCodeHash;

    private LocalDateTime verificationCodeExpiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime verifiedAt;

    public HospitalRegistration() {}

    public Long getRegistrationId() { return registrationId; }
    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
    public String getHospitalEmail() { return hospitalEmail; }
    public void setHospitalEmail(String hospitalEmail) { this.hospitalEmail = hospitalEmail; }
    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public Long getStateId() { return stateId; }
    public void setStateId(Long stateId) { this.stateId = stateId; }
    public Long getDistrictId() { return districtId; }
    public void setDistrictId(Long districtId) { this.districtId = districtId; }
    public HospitalRegistrationStatus getStatus() { return status; }
    public void setStatus(HospitalRegistrationStatus status) { this.status = status; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public String getVerificationCodeHash() { return verificationCodeHash; }
    public void setVerificationCodeHash(String verificationCodeHash) { this.verificationCodeHash = verificationCodeHash; }
    public LocalDateTime getVerificationCodeExpiresAt() { return verificationCodeExpiresAt; }
    public void setVerificationCodeExpiresAt(LocalDateTime verificationCodeExpiresAt) { this.verificationCodeExpiresAt = verificationCodeExpiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}
