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

    @Column(name = "government_hospital_key", unique = true, length = 64)
    private String governmentHospitalKey;

    private Double latitude;
    private Double longitude;

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

    public String getGovernmentHospitalKey() { return governmentHospitalKey; }
    public void setGovernmentHospitalKey(String governmentHospitalKey) { this.governmentHospitalKey = governmentHospitalKey; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
