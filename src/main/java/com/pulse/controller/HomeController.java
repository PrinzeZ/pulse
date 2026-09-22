package com.pulse.controller;

import com.pulse.dto.SearchResult;
import com.pulse.model.StockStatus;
import com.pulse.repository.HospitalRepository;
import com.pulse.service.SearchService;
import com.pulse.local.service.LocalOfflineStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final SearchService searchService;
    private final HospitalRepository hospitalRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public HomeController(SearchService searchService,
                          HospitalRepository hospitalRepository,
                          ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.searchService = searchService;
        this.hospitalRepository = hospitalRepository;
        this.localStoreProvider = localStoreProvider;
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "query", required = false, defaultValue = "") String query,
                       @RequestParam(name = "district", required = false, defaultValue = "") String district,
                       Model model) {
        addSearchModel(query, district, model);
        return "user";
    }

    private void addSearchModel(String query, String district, Model model) {
        List<SearchResult> results = searchService.search(district, query);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("selectedDistrict", district == null ? "" : district);
        model.addAttribute("districts", districts());
        model.addAttribute("results", results);
        model.addAttribute("medicineCards", medicineCards(results));
        model.addAttribute("districtStatuses", districtStatuses(results));
        model.addAttribute("searched", (query != null && !query.isBlank()) || (district != null && !district.isBlank()));
    }

    private List<String> districts() {
        try {
            return hospitalRepository.findAll().stream().map(hospital -> hospital.getDistrict()).distinct().sorted().toList();
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            return local == null ? List.of() : local.districts();
        }
    }


    private List<SearchController.MedicineCard> medicineCards(List<SearchResult> results) {
        Map<Long, SearchController.MedicineCard> grouped = new LinkedHashMap<>();
        for (SearchResult result : results) {
            if (result.getMedicineId() == null) continue;
            SearchController.MedicineCard card = grouped.computeIfAbsent(result.getMedicineId(),
                    id -> new SearchController.MedicineCard(id, result.getMedicineName(), result.getCategory()));
            card.add(result);
        }
        return new java.util.ArrayList<>(grouped.values());
    }

    private Map<String, String> districtStatuses(List<SearchResult> results) {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (SearchResult result : results) {
            String current = statuses.get(result.getDistrict());
            StockStatus candidate = StockStatus.from(result.getQuantity(), result.getThreshold());
            if (current == null || statusRank(candidate) > statusRank(StockStatus.valueOf(current))) {
                statuses.put(result.getDistrict(), candidate.name());
            }
        }
        return statuses;
    }

    private int statusRank(StockStatus status) {
        if (status == StockStatus.RED) return 3;
        if (status == StockStatus.YELLOW) return 2;
        return 1;
    }
}