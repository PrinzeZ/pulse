package com.pulse.service;

import com.pulse.dto.SearchResult;
import com.pulse.model.StockStatus;
import com.pulse.repository.StockEntryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    private final StockEntryRepository stockEntryRepository;

    public SearchServiceImpl(StockEntryRepository stockEntryRepository) {
        this.stockEntryRepository = stockEntryRepository;
    }

    @Override
    public List<SearchResult> search(String district, String medicineQuery) {
        String query = medicineQuery == null ? "" : medicineQuery.trim();
        String selectedDistrict = district == null ? "" : district.trim();
        List<Object[]> rows = stockEntryRepository.searchPublicStock(query);
        List<SearchResult> results = new ArrayList<>();

        for (Object[] row : rows) {
            String hospitalName = (String) row[0];
            String rowDistrict = (String) row[1];
            String medicineName = (String) row[2];
            String category = row[3] == null ? "" : (String) row[3];
            int quantity = ((Number) row[4]).intValue();
            int threshold = ((Number) row[5]).intValue();

            if (!selectedDistrict.isBlank() && !selectedDistrict.equalsIgnoreCase(rowDistrict)) {
                continue;
            }

            results.add(new SearchResult(
                    hospitalName,
                    rowDistrict,
                    medicineName,
                    category,
                    quantity,
                    threshold,
                    StockStatus.from(quantity, threshold).name()));
        }
        return results;
    }
}
