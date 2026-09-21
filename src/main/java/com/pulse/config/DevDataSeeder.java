package com.pulse.config;

import com.pulse.local.service.LocalOfflineStore;
import com.pulse.local.service.StockSyncService;
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
import org.springframework.core.annotation.Order;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Profile("dev")
@Component
@Order(0)
public class DevDataSeeder implements CommandLineRunner {

    private final MedicineRepository medicineRepository;
    private final HospitalRepository hospitalRepository;
    private final StockEntryRepository stockEntryRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final ObjectProvider<StockSyncService> stockSyncServiceProvider;

    @Autowired
    public DevDataSeeder(MedicineRepository medicineRepository, HospitalRepository hospitalRepository,
                         StockEntryRepository stockEntryRepository, ObjectProvider<LocalOfflineStore> localStoreProvider,
                         ObjectProvider<StockSyncService> stockSyncServiceProvider) {
        this.medicineRepository = medicineRepository;
        this.hospitalRepository = hospitalRepository;
        this.stockEntryRepository = stockEntryRepository;
        this.localStoreProvider = localStoreProvider;
        this.stockSyncServiceProvider = stockSyncServiceProvider;
    }

    @Override
    public void run(String... args) {
        System.out.println("=== DEV DATA SEEDER START ===");
        LocalOfflineStore local = localStoreProvider.getIfAvailable();

        // A fresh cloned node must be able to go offline immediately. First try to
        // populate the local reference mirror from the cloud. If the cloud is not
        // reachable, create the deterministic offline demo baseline instead.
        if (local != null) {
            boolean cloudReferenceReady = local.refreshReferenceData();
            if (!cloudReferenceReady && !local.hasLocalData()) {
                local.seedDemoReferenceData();
                System.out.println("LOCAL BOOTSTRAP: cloud unavailable; seeded offline reference data");
            }
        }

        try {
            List<String> medicineNames = Arrays.asList("Paracetamol", "Amoxicillin", "Ibuprofen");
            medicineNames.forEach(this::ensureMedicineExists);
            hospitalRepository.findAll().forEach(hospital -> medicineNames.forEach(m -> ensureStockEntry(hospital, m)));
            System.out.println("DEV VERIFY: medicines=" + medicineRepository.count() + " stock=" + stockEntryRepository.count());
        } catch (RuntimeException ex) {
            if (local != null) {
                if (!local.hasLocalData()) local.seedDemoReferenceData();
                System.out.println("DEV VERIFY: cloud unavailable; local offline data is active");
            } else {
                System.out.println("DEV DATA SEEDER skipped because cloud database is unavailable.");
            }
        }

        if (local != null && local.hasLocalData()) {
            // Populate local stock immediately rather than waiting for the first
            // scheduled sync. This closes the fresh-install/offline startup gap.
            try {
                StockSyncService stockSyncService = stockSyncServiceProvider.getIfAvailable();
                if (stockSyncService != null) stockSyncService.syncKnownHospitals();
            } catch (RuntimeException ignored) {
                // The local seed remains usable when the cloud is unavailable.
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
