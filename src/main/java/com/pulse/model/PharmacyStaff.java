package com.pulse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Column;

@Entity
@DiscriminatorValue("STAFF")
public class PharmacyStaff extends User {

    @Column(name = "hospital_id")
    private Long hospitalId;

    public PharmacyStaff() {}

    public PharmacyStaff(Long userId, String name, String username , String password,Long hospitalId){
        super(userId,name,username,password);
        this.hospitalId = hospitalId;
    }

    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId( Long hospitalId ) { this.hospitalId = hospitalId; }
}