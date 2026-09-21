package com.pulse.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("DISTRICT_ADMIN")
public class DistrictAdmin extends User {
    public DistrictAdmin() {}

    public DistrictAdmin(Long userId, String name, String username, String password) {
        super(userId, name, username, password);
    }

    public void viewDistrictDashboard() {
        System.out.println("District Admin " + getName() + " viewing district dashboard.");
    }
}