package com.pulse.local.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.pulse.service.MedicineRequestService;
import com.pulse.service.StockTransferService;

@Component
@Profile("local")
public class LocalSyncScheduler {

    private final StockSyncService stockSyncService;
    private final LocalOfflineStore localOfflineStore;
    private final MedicineRequestService medicineRequestService;
    private final StockTransferService stockTransferService;
    private final StockLedgerSyncService stockLedgerSyncService;

    public LocalSyncScheduler(StockSyncService stockSyncService,
                              LocalOfflineStore localOfflineStore,
                              MedicineRequestService medicineRequestService,
                              StockTransferService stockTransferService,
                              StockLedgerSyncService stockLedgerSyncService) {
        this.stockSyncService = stockSyncService;
        this.localOfflineStore = localOfflineStore;
        this.medicineRequestService = medicineRequestService;
        this.stockTransferService = stockTransferService;
        this.stockLedgerSyncService = stockLedgerSyncService;
    }

    /** Retry local-to-cloud and cloud-to-local stock synchronization every minute. */
    @Scheduled(fixedDelayString = "${pulse.sync.fixed-delay-ms:60000}", initialDelayString = "${pulse.sync.initial-delay-ms:15000}")
    public void syncKnownHospitals() {
        localOfflineStore.syncPendingStaff();
        localOfflineStore.refreshReferenceData();
        stockSyncService.syncKnownHospitals();
        stockTransferService.syncKnownHospitals();
        stockLedgerSyncService.sync();
        localOfflineStore.hospitals().forEach(hospital ->
                medicineRequestService.syncHospital(hospital.getHospitalId()));
    }
}
