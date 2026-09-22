package com.pulse.controller;

import com.pulse.model.Hospital;
import com.pulse.repository.HospitalRepository;
import com.pulse.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicApiController {
    private final SearchService search;
    private final HospitalRepository hospitals;

    public PublicApiController(SearchService search, HospitalRepository hospitals) { this.search = search; this.hospitals = hospitals; }

    @GetMapping("/stock")
    public List<?> stock(@RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "") String district) {
        return search.search(district, query);
    }

    @GetMapping("/hospitals")
    public List<Map<String,Object>> hospitalDirectory() {
        return hospitals.findAll().stream().sorted(java.util.Comparator.comparing(Hospital::getName)).map(h -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("hospitalId", h.getHospitalId());
            row.put("name", h.getName());
            row.put("district", h.getDistrict());
            row.put("districtId", h.getDistrictId());
            return row;
        }).toList();
    }

    @GetMapping("/status")
    public Map<String,String> status() { return Map.of("service", "P.U.L.S.E", "status", "online"); }
}
