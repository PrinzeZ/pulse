package com.pulse.model;

public class Hospital {
    private Long hospitalId;
    private String name;
    private String district;
    
    public Hospital() {}

    public Hospital(Long hospitalId, String name, String district) {
        this.hospitalId = hospitalId;
        this.name = name;
        this.district = district ; 


    }

    public Long getHospitalId(){ return hospitalId; }
    public void setHospitalid (Long hospitalId) {this.hospitalId = hospitalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
}