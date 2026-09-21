package com.pulse.local.service;

import com.pulse.local.model.LocalStockMovement;
import com.pulse.local.repository.LocalStockMovementRepository;
import com.pulse.model.StockMovement;
import com.pulse.repository.StockMovementRepository;
import com.pulse.service.SupplyChainSchemaService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("local")
public class StockLedgerSyncService {

    private final LocalStockMovementRepository local;
    private final StockMovementRepository cloud;
    private final SupplyChainSchemaService schema;

    public StockLedgerSyncService(LocalStockMovementRepository local,
                                  StockMovementRepository cloud,
                                  SupplyChainSchemaService schema) {
        this.local = local;
        this.cloud = cloud;
        this.schema = schema;
    }

    public SyncResult sync() {
        try {
            if (!schema.ensureTables()) throw new IllegalStateException("Cloud is unavailable.");
            int pushed = 0;
            for (LocalStockMovement item : local.findByPendingSyncTrueOrderByOccurredAtAsc()) {
                if (!cloud.existsByEventId(item.getEventId())) {
                    StockMovement movement = new StockMovement();
                    movement.setEventId(item.getEventId());
                    movement.setHospitalId(item.getHospitalId());
                    movement.setMedicineId(item.getMedicineId());
                    movement.setDeltaQuantity(item.getDeltaQuantity());
                    movement.setMovementType(item.getMovementType());
                    movement.setReferenceType(item.getReferenceType());
                    movement.setReferenceId(item.getReferenceId());
                    movement.setActorUsername(item.getActorUsername());
                    movement.setNote(item.getNote());
                    movement.setOccurredAt(item.getOccurredAt());
                    movement.setPreviousHash(item.getPreviousHash());
                    movement.setHash(item.getHash());
                    cloud.saveAndFlush(movement);
                }
                item.setPendingSync(false);
                local.save(item);
                pushed++;
            }
            return new SyncResult(pushed, local.findByPendingSyncTrueOrderByOccurredAtAsc().size(), "SYNCED");
        } catch (RuntimeException ex) {
            return new SyncResult(0, local.findByPendingSyncTrueOrderByOccurredAtAsc().size(),
                    "OFFLINE: " + ex.getClass().getSimpleName());
        }
    }

    public record SyncResult(int pushed, int pending, String status) {}
}
