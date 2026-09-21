package com.pulse.local.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.model.StockEntry;
import com.pulse.repository.StockEntryRepository;

@Service
@Profile("local")
public class StockSyncService {

    private final StockEntryRepository cloudStock;
    private final LocalStockEntryRepository localStock;

    public StockSyncService(StockEntryRepository cloudStock,
                            LocalStockEntryRepository localStock) {
        this.cloudStock = cloudStock;
        this.localStock = localStock;
    }

    /**
     * Full stock synchronization for one hospital:
     * 1) push local unsynced changes to PostgreSQL;
     * 2) pull the latest PostgreSQL stock into H2 without overwriting local unsynced work.
     */
    public SyncResult syncHospital(Long hospitalId) {
        try {
            int pushed = pushLocalChanges(hospitalId);
            int pulled = pullCloudStock(hospitalId);
            int pending = localStock.findByHospitalIdAndSyncedFalse(hospitalId).size();
            return new SyncResult(hospitalId, pushed, pulled, pending, "SYNCED");
        } catch (RuntimeException ex) {
            int pending = localStock.findByHospitalIdAndSyncedFalse(hospitalId).size();
            return new SyncResult(hospitalId, 0, 0, pending, "OFFLINE: " + ex.getClass().getSimpleName());
        }
    }

    /** Retry synchronization for every hospital already known by the local cache. */
    public List<SyncResult> syncKnownHospitals() {
        Set<Long> hospitalIds = new HashSet<>();
        localStock.findAll().forEach(entry -> {
            if (entry.getHospitalId() != null) hospitalIds.add(entry.getHospitalId());
        });
        return hospitalIds.stream().map(this::syncHospital).toList();
    }

    public int pushLocalChanges(Long hospitalId) {
        int pushed = 0;
        List<LocalStockEntry> pending = localStock.findByHospitalIdAndSyncedFalse(hospitalId);

        for (LocalStockEntry local : pending) {
            StockEntry cloud = cloudStock.findByHospitalIdAndMedId(hospitalId, local.getMedicineId())
                    .orElseGet(() -> new StockEntry(null, hospitalId, local.getMedicineId(), 0));

            cloud.setQuantity(local.getQuantity());
            if (local.getLastUpdated() != null) {
                try {
                    cloud.setLastUpdated(LocalDate.parse(local.getLastUpdated()));
                } catch (RuntimeException ignored) {
                    cloud.setLastUpdated(LocalDate.now());
                }
            } else {
                cloud.setLastUpdated(LocalDate.now());
            }

            StockEntry saved = cloudStock.save(cloud);
            local.setCloudEntryId(saved.getEntryId());
            local.setLastUpdated(saved.getLastUpdated() == null ? LocalDate.now().toString() : saved.getLastUpdated().toString());
            local.setSynced(true);
            localStock.save(local);
            pushed++;
        }
        return pushed;
    }

    public int pullCloudStock(Long hospitalId) {
        int pulled = 0;
        List<StockEntry> cloudEntries = cloudStock.findByHospitalId(hospitalId);
        for (StockEntry cloud : cloudEntries) {
            LocalStockEntry local = localStock.findByCloudEntryId(cloud.getEntryId()).orElse(null);
            if (local == null) {
                local = localStock.findByHospitalIdAndMedicineId(hospitalId, cloud.getMedId()).orElse(null);
            }

            // A brand-new local row is safe to populate from PostgreSQL.
            // An existing unsynced row contains an offline change and must win until it is pushed.
            if (local != null && !local.isSynced()) {
                continue;
            }
            if (local == null) {
                local = new LocalStockEntry();
            }

            local.setCloudEntryId(cloud.getEntryId());
            local.setHospitalId(cloud.getHospitalId());
            local.setMedicineId(cloud.getMedId());
            local.setQuantity(cloud.getQuantity());
            local.setLastUpdated(cloud.getLastUpdated() == null ? null : cloud.getLastUpdated().toString());
            local.setSynced(true);
            localStock.save(local);
            pulled++;
        }

        return pulled;
    }

    public record SyncResult(Long hospitalId, int pushed, int pulled, int pending, String status) {}
}
