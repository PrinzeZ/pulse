package com.pulse.model;

import java.time.LocalDate;

public class StockEntry {
    private Long entryId;
    private Long hospitalId;
    private Long medId;
    
    private int quantity;
    private LocalDate lastUpdated;

    public StockEntry() {}

    public StockEntry(Long entryId, Long hospitalId, Long medId, int quantity) {
        this.entryId = entryId;
        this.hospitalId = hospitalId;
        this.medId = medId;
        this.quantity = quantity;
        this.lastUpdated = LocalDate.now();
    }

    public boolean checkThreshold(Medicine medicine) {
        return this.quantity < medicine.getThreshold();
    }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    
    public Long getMedId() { return medId; }
    public void setMedId(Long medId) { this.medId = medId; }
    
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public LocalDate getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDate lastUpdated) { this.lastUpdated = lastUpdated; }
}