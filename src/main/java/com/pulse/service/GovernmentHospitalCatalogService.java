package com.pulse.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GovernmentHospitalCatalogService {
    private final List<GovernmentHospital> hospitals;

    public GovernmentHospitalCatalogService() {
        this.hospitals = load();
    }

    public List<GovernmentHospital> all() { return hospitals; }

    public Optional<GovernmentHospital> find(String key) {
        if (key == null) return Optional.empty();
        return hospitals.stream().filter(h -> h.key().equals(key.trim())).findFirst();
    }

    public List<GovernmentHospital> byDistrict(String district) {
        if (district == null || district.isBlank()) return all();
        return hospitals.stream().filter(h -> h.district().equalsIgnoreCase(district.trim())).toList();
    }

    private List<GovernmentHospital> load() {
        List<GovernmentHospital> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/government-hospitals.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                if (line.isBlank()) continue;
                List<String> c = parseCsv(line);
                if (c.size() < 8) continue;
                result.add(new GovernmentHospital(
                        c.get(0), c.get(1), c.get(2), c.get(3),
                        Double.parseDouble(c.get(4)), Double.parseDouble(c.get(5)),
                        c.get(6), Boolean.parseBoolean(c.get(7))));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load government hospital directory", ex);
        }
        return List.copyOf(result);
    }

    private static List<String> parseCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"'); i++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                result.add(current.toString()); current.setLength(0);
            } else current.append(ch);
        }
        result.add(current.toString());
        return result;
    }

    public record GovernmentHospital(
            String key,
            String name,
            String district,
            String category,
            double latitude,
            double longitude,
            String address,
            boolean closed) {
        public String googleMapsUrl() {
            return "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;
        }
    }
}
