package com.pulse;

public class SearchResult {
    private String hospital;
    private String medicine;
    private int quantity;
    private String status; 

    public SearchResult(String hospital, String medicine, int quantity, String status) {
        this.hospital = hospital;
        this.medicine = medicine;
        this.quantity = quantity;
        this.status = status;
    }

    public String getHospital() { return hospital; }
    public String getMedicine() { return medicine; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
}
