package com.pulse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;

@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends User {
    public Admin() {
    }

    public Admin(Long userId,String name, String username, String password) {
        super (userId,name,username, password);
    }
    public void viewDashboard() {
        System.out.println("Admin " + name + " viewing dashboard.");
    }
    public void viewStockTrend() {
        System.out.println("Admin " + name + " viewing stock trends.");
    }
    public void generateReport() {
        System.out.println("Admin "+name+ " generating report.");
    }
}