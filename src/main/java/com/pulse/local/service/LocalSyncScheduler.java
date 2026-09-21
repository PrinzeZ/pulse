package com.pulse.local.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalSyncScheduler {

    private final StockSyncService stockSyncService;
    private final LocalOfflineStore localOfflineStore;

    public LocalSyncScheduler(StockSyncService stockSyncService, LocalOfflineStore localOfflineStore) {
        this.stockSyncService = stockSyncService;
        this.localOfflineStore = localOfflineStore;
    }

    /** Retry local-to-cloud and cloud-to-local stock synchronization every minute. */
    @Scheduled(fixedDelayString = "${pulse.sync.fixed-delay-ms:60000}", initialDelayString = "${pulse.sync.initial-delay-ms:15000}")
    public void syncKnownHospitals() {
        localOfflineStore.syncPendingStaff();
        localOfflineStore.refreshReferenceData();
        stockSyncService.syncKnownHospitals();
    }
}
