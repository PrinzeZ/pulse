package com.pulse.service;

import jakarta.transaction.Transactional;

import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.model.StockStatus;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StaffService {

    private final AlertService alertService;
    private final HospitalRepository hospitalRepository;
    private final MedicineRepository medicineRepository;
    private final StockEntryRepository stockEntryRepository;
    private final ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider;

    public StaffService(AlertService alertService,
                        HospitalRepository hospitalRepository,
                        MedicineRepository medicineRepository,
                        StockEntryRepository stockEntryRepository,
                        ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider) {
        this.alertService = alertService;
        this.hospitalRepository = hospitalRepository;
        this.medicineRepository = medicineRepository;
        this.stockEntryRepository = stockEntryRepository;
        this.localStockRepositoryProvider = localStockRepositoryProvider;
    }

    public StaffInventory getInventory(Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        if (hospital == null) {
            return null;
        }

        List<Medicine> medicines = medicineRepository.findAll();
        Map<Long, StockEntry> stockByMedicine;
        LocalStockEntryRepository localRepository = localStockRepositoryProvider.getIfAvailable();
        if (localRepository != null) {
            stockByMedicine = localRepository.findByHospitalId(hospitalId).stream()
                    .collect(Collectors.toMap(
                            LocalStockEntry::getMedicineId,
                            local -> new StockEntry(local.getCloudEntryId(), local.getHospitalId(),
                                    local.getMedicineId(), local.getQuantity()),
                            (a, b) -> b));
        } else {
            stockByMedicine = stockEntryRepository.findByHospitalId(hospitalId)
                    .stream()
                    .collect(Collectors.toMap(StockEntry::getMedId, stock -> stock));
        }
        List<StaffInventoryRow> inventory = new ArrayList<>();
        for (Medicine medicine : medicines) {
            StockEntry stock = stockByMedicine.get(medicine.getMedId());
            if (stock == null) {
                stock = new StockEntry(null, hospitalId, medicine.getMedId(), 0);
            }
            inventory.add(new StaffInventoryRow(
                    medicine,
                    stock,
                    StockStatus.from(stock.getQuantity(), medicine.getThreshold())));
        }
        inventory.sort(Comparator.comparing(row -> row.medicine().getName()));

        List<StaffInventoryRow> thresholdAlerts = inventory.stream()
                .filter(row -> row.stock().getQuantity() < row.medicine().getThreshold())
                .toList();

        return new StaffInventory(hospital, inventory, thresholdAlerts);
    }

    @Transactional
    public StockEntry updateStock(Long hospitalId, Long medicineId, int quantity, Medicine medicine) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (medicine == null) {
            throw new IllegalArgumentException("Medicine is required");
        }

        LocalStockEntryRepository localRepository = localStockRepositoryProvider.getIfAvailable();
        if (localRepository != null) {
            LocalStockEntry local = localRepository.findByHospitalIdAndMedicineId(hospitalId, medicineId)
                    .orElseGet(LocalStockEntry::new);
            local.setHospitalId(hospitalId);
            local.setMedicineId(medicineId);
            local.setQuantity(quantity);
            local.setLastUpdated(LocalDate.now().toString());
            local.setSynced(false);
            LocalStockEntry saved = localRepository.save(local);
            return new StockEntry(saved.getCloudEntryId(), hospitalId, medicineId, quantity);
        }

        StockEntry entry = stockEntryRepository.findByHospitalIdAndMedId(hospitalId, medicineId)
                .orElseGet(() -> new StockEntry(null, hospitalId, medicineId, 0));
        entry.setQuantity(quantity);
        entry.setLastUpdated(LocalDate.now());
        stockEntryRepository.save(entry);

        if (entry.checkThreshold(medicine)) {
            alertService.sendAlert(hospitalId, medicineId, medicine.getName(), quantity);
        }
        return entry;
    }

    public record StaffInventory(Hospital hospital,
                                 List<StaffInventoryRow> inventory,
                                 List<StaffInventoryRow> thresholdAlerts) {
    }

    public record StaffInventoryRow(Medicine medicine, StockEntry stock, StockStatus status) {
    }
}
