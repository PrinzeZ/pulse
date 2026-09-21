package com.pulse.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("STATE_ADMIN")
public class StateAdmin extends User {
    public StateAdmin() {}

    public StateAdmin(Long userId, String name, String username, String password) {
        super(userId, name, username, password);
    }

    public void viewStateDashboard() {
        System.out.println("State Admin " + getName() + " viewing state dashboard.");
    }
}