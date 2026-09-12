package com.pulse.model;

public class PharmacyStaff extends User{
    private Long hospitalId;

    public PharmacyStaff() {}

    public PharmacyStaff(Long userId, String name, String username , String password,Long hospitalId){
        super(userId,name,username,password);
        this.hospitalId = hospitalId;

    }

    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId( Long hospitalId ) { this.hospitalId = hospitalId; }


}