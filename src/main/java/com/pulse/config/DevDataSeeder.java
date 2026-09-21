package com.pulse.config;

import com.pulse.model.Medicine;
import com.pulse.model.Hospital;
import com.pulse.model.StockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.StockEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private final MedicineRepository medicineRepository;
    private final HospitalRepository hospitalRepository;
    private final StockEntryRepository stockEntryRepository;

    @Autowired
    public DevDataSeeder(MedicineRepository medicineRepository, HospitalRepository hospitalRepository, StockEntryRepository stockEntryRepository) {
        this.medicineRepository = medicineRepository;
        this.hospitalRepository = hospitalRepository;
        this.stockEntryRepository = stockEntryRepository;
    }

    @Override
    public void run(String... args) {
        System.out.println("=== DEV DATA SEEDER START ===");

        List<String> medicineNames = Arrays.asList("Paracetamol", "Amoxicillin", "Ibuprofen");
        medicineNames.forEach(this::ensureMedicineExists);

        List<Hospital> hospitals = hospitalRepository.findAll();
        hospitals.forEach(hospital -> {
            medicineNames.forEach(medicineName -> {
                ensureStockEntry(hospital, medicineName);
            });
        });

        int medicines = (int) medicineRepository.count();
        int stock = (int) stockEntryRepository.count();
        System.out.println("DEV VERIFY: medicines=" + medicines + " stock=" + stock);
        System.out.println("=== DEV DATA SEEDER COMPLETE ===");
    }

    private void ensureMedicineExists(String name) {
        Medicine medicine = new Medicine();
        medicine.setName(name);
        // Set default category and threshold if needed
        medicine.setCat("General");
        medicine.setThreshold(50);
        medicineRepository.save(medicine);
    }

    private void ensureStockEntry(Hospital hospital, String medicineName) {
        // Find medicine by name (assuming names are unique)
        Medicine medicine = medicineRepository.findAll().stream()
                .filter(m -> m.getName().equals(medicineName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Medicine not found: " + medicineName));

        // Check if stock entry exists
        if (stockEntryRepository.findByHospitalIdAndMedId(hospital.getHospitalId(), medicine.getMedId()).isEmpty()) {
            StockEntry stockEntry = new StockEntry();
            stockEntry.setHospitalId(hospital.getHospitalId());
            stockEntry.setMedId(medicine.getMedId());
            stockEntry.setQuantity(100);
            stockEntry.setLastUpdated(LocalDate.now());
            stockEntryRepository.save(stockEntry);
        }
    }
}