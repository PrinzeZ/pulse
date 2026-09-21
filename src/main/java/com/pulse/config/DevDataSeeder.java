package com.pulse.config;

import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Medicine;
import com.pulse.model.Hospital;
import com.pulse.model.StockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.StockEntryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Profile("dev")
@Component
public class DevDataSeeder implements CommandLineRunner {

    private final MedicineRepository medicineRepository;
    private final HospitalRepository hospitalRepository;
    private final StockEntryRepository stockEntryRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    @Autowired
    public DevDataSeeder(MedicineRepository medicineRepository, HospitalRepository hospitalRepository,
                         StockEntryRepository stockEntryRepository, ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.medicineRepository = medicineRepository;
        this.hospitalRepository = hospitalRepository;
        this.stockEntryRepository = stockEntryRepository;
        this.localStoreProvider = localStoreProvider;
    }

    @Override
    public void run(String... args) {
        System.out.println("=== DEV DATA SEEDER START ===");
        try {
            List<String> medicineNames = Arrays.asList("Paracetamol", "Amoxicillin", "Ibuprofen");
            medicineNames.forEach(this::ensureMedicineExists);
            hospitalRepository.findAll().forEach(hospital -> medicineNames.forEach(m -> ensureStockEntry(hospital, m)));
            System.out.println("DEV VERIFY: medicines=" + medicineRepository.count() + " stock=" + stockEntryRepository.count());
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            if (local != null) {
                local.seedDemoReferenceData();
                System.out.println("DEV VERIFY: cloud unavailable; local demo data is active");
            } else {
                System.out.println("DEV DATA SEEDER skipped because cloud database is unavailable.");
            }
        }
        System.out.println("=== DEV DATA SEEDER COMPLETE ===");
    }

    private void ensureMedicineExists(String name) {
        if (medicineRepository.findByName(name).isPresent()) return;
        Medicine medicine = new Medicine(); medicine.setName(name); medicine.setCat("General"); medicine.setThreshold(50); medicineRepository.save(medicine);
    }

    private void ensureStockEntry(Hospital hospital, String medicineName) {
        Medicine medicine = medicineRepository.findByName(medicineName).orElseThrow();
        if (stockEntryRepository.findByHospitalIdAndMedId(hospital.getHospitalId(), medicine.getMedId()).isEmpty()) {
            StockEntry stockEntry = new StockEntry(); stockEntry.setHospitalId(hospital.getHospitalId()); stockEntry.setMedId(medicine.getMedId());
            stockEntry.setQuantity(100); stockEntry.setLastUpdated(LocalDate.now()); stockEntryRepository.save(stockEntry);
        }
    }
}
