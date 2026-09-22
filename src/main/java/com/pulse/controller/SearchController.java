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
public class SearchController {

    private final SearchService searchService;
    private final HospitalRepository hospitalRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public SearchController(SearchService searchService, HospitalRepository hospitalRepository, ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.searchService = searchService;
        this.hospitalRepository = hospitalRepository;
        this.localStoreProvider = localStoreProvider;
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "query", required = false, defaultValue = "") String query,
                         @RequestParam(name = "district", required = false, defaultValue = "") String district,
                         Model model) {
        List<SearchResult> results = searchService.search(district, query);
        model.addAttribute("results", results);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("selectedDistrict", district == null ? "" : district);
        model.addAttribute("district", district == null ? "" : district);
        model.addAttribute("districts", districts());
        model.addAttribute("districtStatuses", districtStatuses(results));
        model.addAttribute("searched", (query != null && !query.isBlank()) || (district != null && !district.isBlank()));
        return "search";
    }

    private List<String> districts() {
        try {
            return hospitalRepository.findAll().stream().map(hospital -> hospital.getDistrict()).distinct().sorted().toList();
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            return local == null ? List.of() : local.districts();
        }
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
