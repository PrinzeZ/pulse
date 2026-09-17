package com.pulse.dto;

public class SearchResult {
    private String hospitalName;
    private String district;
    private String medicineName;
    private int quantity;
    private int threshold;

    // Constructor, getters, and setters
    public SearchResult(String hospitalName, String district, String medicineName, int quantity, int threshold) {
        this.hospitalName = hospitalName;
        this.district = district;
        this.medicineName = medicineName;
        this.quantity = quantity;
        this.threshold = threshold;
    }

    public String getHospitalName() { return hospitalName; }
    public String getDistrict() { return district; }
    public String getMedicineName() { return medicineName; }
    public int getQuantity() { return quantity; }
    public int getThreshold() { return threshold; }
}