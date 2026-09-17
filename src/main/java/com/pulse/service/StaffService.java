package com.pulse.service;

import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
// this is basically telling the sprinjg container to detect the class during class path scanning and register it as a bean 
@Service
public class StaffService {

    @Autowired // Spring to automatically inject a collaborating bean/object into a class constructor, field and like u dont need to create objects using new keyword hehe
    // Spring ginds the matching container and wires it autooooo matically
    private AlertService alertService;
   
    // THE PIPELINE — this is your «include» chain from the Use Case Diagram
    public void updateStock(Long hospitalId, Long medicineId, int quantity, Medicine medicine) {
        // Step 1: Create the stock entry
        StockEntry entry = new StockEntry(null, hospitalId, medicineId, quantity);

        // Step 2: Save to DB (Sanu's StockRepository plugs in here)
        // stockRepository.save(entry);  ← sanu needs to uncomment when ur repo is ready

        // Step 3: MANDATORY threshold check — the «include»
        boolean isLow = entry.checkThreshold(medicine);

        // Step 4: Auto-alert if low — the System actor fires
        if (isLow) {
            alertService.sendAlert(hospitalId, medicineId, medicine.getName(), quantity);
        }

        // Step 5: Audit log ( GGGGouri's AuditLogger plugs in here)
        // auditLogger.log("UPDATE_STOCK", username, medicine.getName() + " qty=" + quantity);
    }// slthough i used ai in this file to help me with the code its still messy i need to fix it after yall put ur stuff in 
}