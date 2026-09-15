package com.pulse.dto;

public class SearchResult {
    private String hospitalName;
    private String district;
    private String medicineName;
    private String status;
    private int quantity;
    private int threshold;

    public SearchResult() {}

    public SearchResult(String hospitalName, String district, String medicineName, String status, int quantity, int threshold) {
        this.hospitalName = hospitalName;
        this.district = district;
        this.medicineName = medicineName;
        this.status = status;
        this.quantity = quantity;
        this.threshold = threshold;
    }

    public SearchResult(String hospitalName, String medicineName, String status, int quantity, int threshold) {
        this.hospitalName = hospitalName;
        this.medicineName = medicineName;
        this.status = status;
        this.quantity = quantity;
        this.threshold = threshold;
    }

    public String getHospitalName() { return hospitalName; }
    public String getDistrict() { return district; }
    public String getMedicineName() { return medicineName; }
    public String getStatus() { return status; }
    public int getQuantity() { return quantity; }
    public int getThreshold() { return threshold; }
}