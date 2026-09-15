package com.pulse.service;

import com.pulse.model.Alert;
import com.pulse.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertService implements Notifiable {

    private final AlertRepository alertRepository;

    @Autowired
    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public void sendAlert(String message) {
        System.out.println("🚨 ALERT: " + message);
    }

    public void sendAlert(long hospitalId, long medId, String medName, int quantity) {
        String message = "LOW STOCK: " + medName + " at hospital " + hospitalId + " (qty: " + quantity + ")";
        System.out.println("🚨 " + message);

        Alert alert = new Alert(hospitalId, medId, message);
        alertRepository.save(alert);
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByResolvedFalse();
    }
}