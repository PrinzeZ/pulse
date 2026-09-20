package com.pulse.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "medicines")
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medicine_id")
    private Long medId;

    private String name;
    private String category;
    private int threshold;
    private LocalDate expiryDate; // Expiry date of the medicine

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

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}