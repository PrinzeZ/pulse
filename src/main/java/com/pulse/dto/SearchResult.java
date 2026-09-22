package com.pulse.dto;

public class SearchResult {
    private final Long medicineId;
    private final String hospitalName;
    private final String district;
    private final String medicineName;
    private final String category;
    private final int quantity;
    private final int threshold;
    private final String status;

    public SearchResult(Long medicineId, String hospitalName, String district, String medicineName, String category,
                        int quantity, int threshold, String status) {
        this.medicineId = medicineId;
        this.hospitalName = hospitalName;
        this.district = district;
        this.medicineName = medicineName;
        this.category = category;
        this.quantity = quantity;
        this.threshold = threshold;
        this.status = status;
    }

    public Long getMedicineId() { return medicineId; }
    public String getHospitalName() { return hospitalName; }
    public String getDistrict() { return district; }
    public String getMedicineName() { return medicineName; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public int getThreshold() { return threshold; }
    public String getStatus() { return status; }
}
