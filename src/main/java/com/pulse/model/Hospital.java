package com.pulse.model;

import jakarta.persistence.*;

@Entity
@Table(name = "hospitals")
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hospital_id")
    private Long hospitalId;

    private String name;
    private String district;

    @Column(name = "district_id")
    private Long districtId;

    public Hospital() {}

    public Hospital(Long hospitalId, String name, String district) {
        this.hospitalId = hospitalId;
        this.name = name;
        this.district = district;
    }

    public Long getHospitalId() {
        return hospitalId;
    }

    public void setHospitalid(Long hospitalId) {
        this.hospitalId = hospitalId;
    }

    public void setHospitalId(Long hospitalId) {
        this.hospitalId = hospitalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public Long getDistrictId() {
        return districtId;
    }

    public void setDistrictId(Long districtId) {
        this.districtId = districtId;
    }
}
