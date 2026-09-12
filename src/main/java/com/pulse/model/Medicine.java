package com.pulse.model;

public class Medicine {
    private Long medId;
    private String name;
    private String category;
    private int threshold; // Per medicine for each the threshold - redesign cheyyendivarum

    public Medicine() {}

    public Medicine(Long medId, String name, String category, int threshold){
        this.medId= medId;
        this.name = name;
        this.category = category;
        this.threshold = threshold;
    }
    public Long getMedId() { return medId;}
    public void setMedId(Long medId){ this.medId = medId; }    
    public String getName() {return name;}
    public void setName(String name) { this.name = name;}
    public String getCat() {return category;}
    public void setCat(String category) {this.category= category;}
    public int getThreshold() { return threshold;}
    public void setThreshold(int threshold) { this.threshold = threshold; }
}