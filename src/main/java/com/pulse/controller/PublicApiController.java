package com.pulse.controller;

import com.pulse.model.Hospital;
import com.pulse.repository.HospitalRepository;
import com.pulse.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/public")
public class PublicApiController {
    private static final long LAN_TTL_MILLIS = 30_000L;
    private static final AtomicReference<LanRegistration> LAN = new AtomicReference<>();
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

    @PostMapping("/lan/register")
    public Map<String, Object> registerLan(
            @RequestHeader(value = "X-Pulse-Lan-Token", required = false) String token,
            @RequestBody String lanUrl) {
        String expected = System.getenv("PULSE_LAN_REGISTRATION_TOKEN");
        if (expected != null && !expected.isBlank() && !expected.equals(token)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid LAN registration token");
        }

        String normalized = normalizeLanUrl(lanUrl);
        if (normalized == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid LAN URL");
        }
        LAN.set(new LanRegistration(normalized, System.currentTimeMillis()));
        return Map.of("registered", true, "url", normalized);
    }

    @GetMapping("/lan")
    public Map<String, Object> currentLan() {
        LanRegistration registration = LAN.get();
        if (registration == null || System.currentTimeMillis() - registration.registeredAt() > LAN_TTL_MILLIS) {
            return Map.of("available", false);
        }
        return Map.of("available", true, "url", registration.url());
    }

    private static String normalizeLanUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(value.trim());
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getPort() != 8080) return null;
            if (!(host.startsWith("10.") || host.startsWith("192.168.") || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))) return null;
            return "http://" + host + ":8080";
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record LanRegistration(String url, long registeredAt) {}
}
