package com.pulse.service;

import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Alert;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertService implements Notifiable {

    private final AlertRepository alertRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final MedicineRepository medicineRepository;
    private final StockEntryRepository stockEntryRepository;

    public AlertService(AlertRepository alertRepository, ObjectProvider<LocalOfflineStore> localStoreProvider,
                        MedicineRepository medicineRepository, StockEntryRepository stockEntryRepository) {
        this.alertRepository = alertRepository;
        this.localStoreProvider = localStoreProvider;
        this.medicineRepository = medicineRepository;
        this.stockEntryRepository = stockEntryRepository;
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
            synchronizeCloudAlerts();
            return alertRepository.findByResolvedFalseOrderByCreatedAtDesc();
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            if (local == null) throw ex;
            return local.hospitals().stream().flatMap(h -> local.stockForHospital(h.getHospitalId()).stream()
                    .map(stock -> localAlert(h, stock, local))
                    .filter(java.util.Objects::nonNull).toList().stream())
                    .toList();
        }
    }

    private void synchronizeCloudAlerts() {
        var medicines = new java.util.HashMap<Long, Medicine>();
        medicineRepository.findAll().forEach(m -> medicines.put(m.getMedId(), m));
        var active = new java.util.HashMap<String, Alert>();
        alertRepository.findByResolvedFalseOrderByCreatedAtDesc().forEach(a -> active.put(a.getHospitalId() + ":" + a.getmedId(), a));
        for (var stock : stockEntryRepository.findAll()) {
            Medicine medicine = medicines.get(stock.getMedId());
            if (medicine == null) continue;
            String key = stock.getHospitalId() + ":" + stock.getMedId();
            boolean low = stock.getQuantity() <= medicine.getThreshold();
            Alert existing = active.get(key);
            if (low && existing == null) {
                alertRepository.save(new Alert(stock.getHospitalId(), stock.getMedId(),
                        "LOW STOCK: " + medicine.getName() + " at hospital " + stock.getHospitalId() + " (qty: " + stock.getQuantity() + ")"));
            } else if (!low && existing != null) {
                existing.setResolved(true);
                alertRepository.save(existing);
            } else if (low && existing != null) {
                existing.setMessage("LOW STOCK: " + medicine.getName() + " at hospital " + stock.getHospitalId() + " (qty: " + stock.getQuantity() + ")");
                alertRepository.save(existing);
            }
        }
    }

    private Alert localAlert(Hospital hospital, LocalStockEntry stock, LocalOfflineStore local) {
        Medicine medicine = local.findMedicine(stock.getMedicineId()).orElse(null);
        if (medicine == null || stock.getQuantity() >= medicine.getThreshold()) return null;
        return new Alert(hospital.getHospitalId(), medicine.getMedId(),
                "LOW STOCK: " + medicine.getName() + " at " + hospital.getName() + " (qty: " + stock.getQuantity() + ")");
    }
}
