package com.pulse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;

@Entity
@DiscriminatorValue("STAFF")
public class PharmacyStaff extends User {


    public PharmacyStaff() {}

    public PharmacyStaff(Long userId, String name, String username , String password,Long hospitalId){
        super(userId,name,username,password);
        this.hospitalId = hospitalId;
    }

}