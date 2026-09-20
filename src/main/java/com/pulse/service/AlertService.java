package com.pulse.service;

import com.pulse.model.Alert;
import com.pulse.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertService implements Notifiable {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public void sendAlert(String message) {
        System.out.println("ALERT: " + message);
    }

    public void sendAlert(long hospitalId, long medId, String medName, int quantity) {
        if (!alertRepository.findByHospitalIdAndMedIdAndResolvedFalse(hospitalId, medId).isEmpty()) {
            return;
        }
        String message = "LOW STOCK: " + medName + " at hospital " + hospitalId + " (qty: " + quantity + ")";
        sendAlert(message);
        alertRepository.save(new Alert(hospitalId, medId, message));
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByResolvedFalseOrderByCreatedAtDesc();
    }
}
