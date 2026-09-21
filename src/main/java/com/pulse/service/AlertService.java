package com.pulse.service;

import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Alert;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.repository.AlertRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertService implements Notifiable {

    private final AlertRepository alertRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public AlertService(AlertRepository alertRepository, ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.alertRepository = alertRepository;
        this.localStoreProvider = localStoreProvider;
    }

    @Override
    public void sendAlert(String message) {
        System.out.println("ALERT: " + message);
    }

    public void sendAlert(long hospitalId, long medId, String medName, int quantity) {
        try {
            if (!alertRepository.findByHospitalIdAndMedIdAndResolvedFalse(hospitalId, medId).isEmpty()) return;
            String message = "LOW STOCK: " + medName + " at hospital " + hospitalId + " (qty: " + quantity + ")";
            sendAlert(message);
            alertRepository.save(new Alert(hospitalId, medId, message));
        } catch (RuntimeException ex) {
            // Offline mode derives alerts from the local stock mirror instead of requiring PostgreSQL.
            sendAlert("LOW STOCK: " + medName + " at hospital " + hospitalId + " (qty: " + quantity + ")");
        }
    }

    public List<Alert> getActiveAlerts() {
        try {
            return alertRepository.findByResolvedFalseOrderByCreatedAtDesc();
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            if (local == null) throw ex;
            return local.hospitals().stream().flatMap(h -> local.stockForHospital(h.getHospitalId()).stream()
                    .map(stock -> localAlert(h, stock, local))
                    .filter(java.util.Objects::nonNull)).toList();
        }
    }

    private Alert localAlert(Hospital hospital, LocalStockEntry stock, LocalOfflineStore local) {
        Medicine medicine = local.findMedicine(stock.getMedicineId()).orElse(null);
        if (medicine == null || stock.getQuantity() >= medicine.getThreshold()) return null;
        return new Alert(hospital.getHospitalId(), medicine.getMedId(),
                "LOW STOCK: " + medicine.getName() + " at " + hospital.getName() + " (qty: " + stock.getQuantity() + ")");
    }
}
