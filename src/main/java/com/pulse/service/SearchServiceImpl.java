package com.pulse.service;

import com.pulse.dto.SearchResult;
import com.pulse.model.StockStatus;
import com.pulse.repository.StockEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private StockEntryRepository stockEntryRepository;

    @Override
    public List<SearchResult> search(String district, String medicineQuery) {
        // Row shape from the native query: hospital_name, district, medicine_name, quantity, threshold
        List<Object[]> rows = stockEntryRepository.searchPublicStock(medicineQuery);

        List<SearchResult> results = new ArrayList<>();

        for (Object[] row : rows) {
            String hospitalName = (String) row[0];
            String rowDistrict = (String) row[1];
            String medicineName = (String) row[2];

            // Native queries return generic Object types — .intValue() works
            // regardless of whether the driver hands back Integer, Long, or BigDecimal.
            int quantity = ((Number) row[3]).intValue();
            int threshold = ((Number) row[4]).intValue();

            // The repository query doesn't filter by district itself, so we do it here.
            if (district != null && !district.isBlank() && !district.equalsIgnoreCase(rowDistrict)) {
                continue;
            }

            StockStatus status = StockStatus.from(quantity, threshold);
            results.add(new SearchResult(hospitalName, medicineName, quantity, status.name()));
        }

        return results;
    }
}
