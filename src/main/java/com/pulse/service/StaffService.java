package com.pulse.service;

import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class StaffService {

    @Autowired
    private AlertService alertService;

    @Autowired
    private StockEntryRepository stockRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    public void updateStock(Long hospitalId, Long medicineId, int quantity) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found with ID: " + medicineId));
        updateStock(hospitalId, medicineId, quantity, medicine);
    }

    // THE PIPELINE — this is the «include» chain from the Use Case Diagram
    public void updateStock(Long hospitalId, Long medicineId, int quantity, Medicine medicine) {
        // Step 1: Find existing stock or create a new entry
        Optional<StockEntry> existing = stockRepository.findByHospitalIdAndMedId(hospitalId, medicineId);
        StockEntry entry;
        if (existing.isPresent()) {
            entry = existing.get();
            entry.setQuantity(quantity);
            entry.setLastUpdated(LocalDate.now());
        } else {
            entry = new StockEntry(null, hospitalId, medicineId, quantity);
        }

        // Step 2: Save to DB
        stockRepository.save(entry);

        // Step 3: MANDATORY threshold check — the «include»
        boolean isLow = entry.checkThreshold(medicine);

        // Step 4: Auto-alert if low — the System actor fires
        if (isLow) {
            alertService.sendAlert(hospitalId, medicineId, medicine.getName(), quantity);
        }
    }
}