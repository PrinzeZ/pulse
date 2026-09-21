package com.pulse.local.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.model.StockEntry;
import com.pulse.repository.StockEntryRepository;

@Service
public class StockSyncService {

    private final StockEntryRepository stockEntryRepository;
    private final LocalStockEntryRepository localStockEntryRepository;

    public StockSyncService(
            StockEntryRepository stockEntryRepository,
            LocalStockEntryRepository localStockEntryRepository) {

        this.stockEntryRepository = stockEntryRepository;
        this.localStockEntryRepository = localStockEntryRepository;
    }

    public void downloadHospitalStock(Long hospitalId) {

        // Get hospital stock from PostgreSQL
        List<StockEntry> cloudStock =
                stockEntryRepository.findByHospitalId(hospitalId);

        // List that will contain the H2 records
        List<LocalStockEntry> localStock = new ArrayList<>();

        for (StockEntry cloudEntry : cloudStock) {

            LocalStockEntry localEntry =
                    localStockEntryRepository
                            .findById(cloudEntry.getEntryId())
                            .orElse(new LocalStockEntry());

            // Copy PostgreSQL data to H2
            localEntry.setEntryId(cloudEntry.getEntryId());
            localEntry.setHospitalId(cloudEntry.getHospitalId());
            localEntry.setMedicineId(cloudEntry.getMedId());
            localEntry.setQuantity(cloudEntry.getQuantity());

            if (cloudEntry.getLastUpdated() != null) {
                localEntry.setLastUpdated(
                        cloudEntry.getLastUpdated().toString()
                );
            }

            // This record currently matches PostgreSQL
            localEntry.setSynced(true);

            localStock.add(localEntry);
        }

        // Save everything into H2
        localStockEntryRepository.saveAll(localStock);
    }
}

