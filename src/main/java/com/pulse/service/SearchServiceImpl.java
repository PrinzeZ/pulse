package com.pulse.service;

import com.pulse.dto.SearchResult;
import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.model.StockStatus;
import com.pulse.repository.StockEntryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private final StockEntryRepository stockEntryRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final ObjectProvider<LocalStockEntryRepository> localStockProvider;

    public SearchServiceImpl(StockEntryRepository stockEntryRepository,
                             ObjectProvider<LocalOfflineStore> localStoreProvider,
                             ObjectProvider<LocalStockEntryRepository> localStockProvider) {
        this.stockEntryRepository = stockEntryRepository;
        this.localStoreProvider = localStoreProvider;
        this.localStockProvider = localStockProvider;
    }

    @Override
    public List<SearchResult> search(String district, String medicineQuery) {
        String query = medicineQuery == null ? "" : medicineQuery.trim();
        String selectedDistrict = district == null ? "" : district.trim();

        try {
            return searchCloud(selectedDistrict, query);
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            if (local == null || !local.hasLocalData()) throw ex;
            return searchLocal(local, selectedDistrict, query);
        }
    }

    private List<SearchResult> searchCloud(String selectedDistrict, String query) {
        List<Object[]> rows = stockEntryRepository.searchPublicStock(query);
        List<SearchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            String hospitalName = (String) row[0];
            String rowDistrict = (String) row[1];
            String medicineName = (String) row[2];
            String category = row[3] == null ? "" : (String) row[3];
            int quantity = ((Number) row[4]).intValue();
            int threshold = ((Number) row[5]).intValue();
            if (!selectedDistrict.isBlank() && !selectedDistrict.equalsIgnoreCase(rowDistrict)) continue;
            results.add(new SearchResult(hospitalName, rowDistrict, medicineName, category, quantity, threshold,
                    StockStatus.from(quantity, threshold).name()));
        }
        return results;
    }

    private List<SearchResult> searchLocal(LocalOfflineStore local, String district, String query) {
        Map<Long, Hospital> hospitals = local.hospitals().stream()
                .collect(Collectors.toMap(Hospital::getHospitalId, h -> h));
        Map<Long, Medicine> medicines = local.medicines().stream()
                .collect(Collectors.toMap(Medicine::getMedId, m -> m));
        List<SearchResult> results = new ArrayList<>();
        LocalStockEntryRepository stock = localStockProvider.getIfAvailable();
        if (stock == null) return results;

        String q = query.toLowerCase();
        for (LocalStockEntry entry : stock.findAll()) {
            Hospital hospital = hospitals.get(entry.getHospitalId());
            Medicine medicine = medicines.get(entry.getMedicineId());
            if (hospital == null || medicine == null) continue;
            if (!district.isBlank() && !district.equalsIgnoreCase(hospital.getDistrict())) continue;
            if (!q.isBlank() && !medicine.getName().toLowerCase().contains(q)
                    && !hospital.getName().toLowerCase().contains(q)) continue;
            results.add(new SearchResult(hospital.getName(), hospital.getDistrict(), medicine.getName(),
                    medicine.getCat() == null ? "" : medicine.getCat(), entry.getQuantity(), medicine.getThreshold(),
                    StockStatus.from(entry.getQuantity(), medicine.getThreshold()).name()));
        }
        results.sort(java.util.Comparator.comparing(SearchResult::getMedicineName)
                .thenComparing(SearchResult::getHospitalName));
        return results;
    }
}
