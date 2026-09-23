package com.pulse.service;

import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.local.service.LocalOfflineStore;
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
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final StockLedgerService stockLedgerService;

    public StaffService(AlertService alertService,
                        HospitalRepository hospitalRepository,
                        MedicineRepository medicineRepository,
                        StockEntryRepository stockEntryRepository,
                        ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider,
                        ObjectProvider<LocalOfflineStore> localStoreProvider,
                        StockLedgerService stockLedgerService) {
        this.alertService = alertService;
        this.hospitalRepository = hospitalRepository;
        this.medicineRepository = medicineRepository;
        this.stockEntryRepository = stockEntryRepository;
        this.localStockRepositoryProvider = localStockRepositoryProvider;
        this.localStoreProvider = localStoreProvider;
        this.stockLedgerService = stockLedgerService;
    }

    /**
     * Build the staff inventory from the local H2 node whenever the local profile
     * is active. The cloud database is only used when the application is running
     * without the local profile.
     */
    public StaffInventory getInventory(Long hospitalId) {
        LocalOfflineStore localStore = localStoreProvider.getIfAvailable();
        LocalStockEntryRepository localRepository = localStockRepositoryProvider.getIfAvailable();

        if (localStore != null && localRepository != null) {
            Hospital hospital = localStore.findHospital(hospitalId).orElse(null);
            if (hospital == null) {
                return null;
            }

            List<Medicine> medicines = localStore.medicines();
            Map<Long, LocalStockEntry> localStockByMedicine = localRepository.findByHospitalId(hospitalId)
                    .stream()
                    .collect(Collectors.toMap(
                            LocalStockEntry::getMedicineId,
                            entry -> entry,
                            (first, second) -> second));

            List<StaffInventoryRow> inventory = new ArrayList<>();
            for (Medicine medicine : medicines) {
                LocalStockEntry local = localStockByMedicine.get(medicine.getMedId());
                StockEntry stock = local == null
                        ? new StockEntry(null, hospitalId, medicine.getMedId(), 0)
                        : toCloudView(local);

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

        Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        if (hospital == null) {
            return null;
        }

        List<Medicine> medicines = medicineRepository.findAll();
        Map<Long, StockEntry> stockByMedicine = stockEntryRepository.findByHospitalId(hospitalId)
                .stream()
                .collect(Collectors.toMap(StockEntry::getMedId, stock -> stock, (first, second) -> second));

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

    /**
     * Staff stock writes are local-first.
     *
     * In local mode, the only database operation performed by this method is the
     * LocalStockEntryRepository save. Spring Data gives that repository its local
     * H2 transaction manager through LocalJpaConfig. No PostgreSQL repository is
     * touched, so loss of Internet cannot roll back the local write.
     *
     * In non-local mode, the original PostgreSQL write path is retained.
     */
    public boolean updateStock(Long hospitalId, Long medicineId, int quantity, Medicine medicine) {
        return updateStock(hospitalId, medicineId, quantity, medicine, null);
    }

    public boolean updateStock(Long hospitalId, Long medicineId, int quantity, Medicine medicine, String actor) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (medicine == null) {
            throw new IllegalArgumentException("Medicine is required");
        }

        LocalOfflineStore localStore = localStoreProvider.getIfAvailable();
        LocalStockEntryRepository localRepository = localStockRepositoryProvider.getIfAvailable();

        if (localStore != null && localRepository != null) {
            LocalStockEntry local = localRepository.findByHospitalIdAndMedicineId(hospitalId, medicineId)
                    .orElseGet(LocalStockEntry::new);

            int previousQuantity = local.getQuantity();
            local.setHospitalId(hospitalId);
            local.setMedicineId(medicineId);
            local.setQuantity(quantity);
            local.setLastUpdated(LocalDate.now().toString());
            local.setSynced(false);

            localRepository.saveAndFlush(local);
            stockLedgerService.recordLocal(hospitalId, medicineId, quantity - previousQuantity,
                    "MANUAL_ADJUSTMENT", "STOCK_ENTRY", local.getEntryId(), actor, "Staff stock quantity adjustment.", previousQuantity, quantity);

            // Alerts in local mode are derived from local stock by AlertService.
            // Do not call the cloud alert path here; it would reintroduce a
            // PostgreSQL dependency into the offline write request.
            return true;
        }

        StockEntry entry = stockEntryRepository.findByHospitalIdAndMedId(hospitalId, medicineId)
                .orElseGet(() -> new StockEntry(null, hospitalId, medicineId, 0));
        int previousQuantity = entry.getQuantity();
        entry.setQuantity(quantity);
        entry.setLastUpdated(LocalDate.now());
        stockEntryRepository.saveAndFlush(entry);
        try {
            stockLedgerService.recordCloud(hospitalId, medicineId, quantity - previousQuantity,
                    "MANUAL_ADJUSTMENT", "STOCK_ENTRY", entry.getEntryId(), actor, "Staff stock quantity adjustment.", previousQuantity, quantity);
        } catch (RuntimeException ignored) {
            // Stock remains authoritative; the audit ledger can be retried separately.
        }

        if (entry.checkThreshold(medicine)) {
            alertService.sendAlert(hospitalId, medicineId, medicine.getName(), quantity);
        }
        return true;
    }

    private StockEntry toCloudView(LocalStockEntry local) {
        StockEntry stock = new StockEntry(
                local.getCloudEntryId(),
                local.getHospitalId(),
                local.getMedicineId(),
                local.getQuantity());

        if (local.getLastUpdated() != null) {
            try {
                stock.setLastUpdated(LocalDate.parse(local.getLastUpdated()));
            } catch (RuntimeException ignored) {
                // Keep the constructor's current-date fallback.
            }
        }
        return stock;
    }

    public record StaffInventory(Hospital hospital,
                                 List<StaffInventoryRow> inventory,
                                 List<StaffInventoryRow> thresholdAlerts) {
    }

    public record StaffInventoryRow(Medicine medicine, StockEntry stock, StockStatus status) {
    }
}
