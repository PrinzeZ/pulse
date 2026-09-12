package com.pulse.service;
// will be adding multiple comments for u to understand this file its bit much
import com.pulse.model.Alert;
import org.springframework.stereotype.Service;
// make sure u note which all packages being imported as in like how ive added the packages here(fyi packages signify folders )
import java.util.ArrayList;
import java.util.List;

@Service
public class AlertService implements Notifiable {


    // Sanu needs to wire this to the alerts table later budddy
    private List<Alert> alerts = new ArrayList<>();

    @Override // meant to show us this is meant to be overridden if u didnt know 
    public void sendAlert(String message) {
        // v1: print to console (works Monday)
        // v2: save to DB via AlertRepository (Sanu :) get to work )
        System.out.println("🚨 ALERT: " + message);

        // Parse hospital/medicine from message for the Alert object
        // (In real integration, "StaffService" passes IDs directly through SO Make it absolute certainty all u dum dums understand - see below )
    }

    // Overloaded version for the real flow
    public void sendAlert(long hospitalId, long medId, String medName, int quantity) {
        String message = "LOW STOCK: " + medName + " at hospital " + hospitalId + " (qty: " + quantity + ")";
        System.out.println("🚨 " + message); // not easy to find weird ahh emoji

        Alert alert = new Alert(hospitalId, medId, message);
        alerts.add(alert);
    }

    public List<Alert> getActiveAlerts() {
        return alerts.stream().filter(a -> !a.isResolved()).toList(); // basically it tell for each alert if the test is resolved or not 
    }
}