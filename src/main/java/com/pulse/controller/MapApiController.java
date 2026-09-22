package com.pulse.controller;

import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.model.StockStatus;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.service.GovernmentHospitalCatalogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/map")
public class MapApiController {
    private final GovernmentHospitalCatalogService catalog;
    private final HospitalRepository hospitals;
    private final MedicineRepository medicines;
    private final StockEntryRepository stock;
    private final ObjectProvider<LocalOfflineStore> localProvider;
    private final ObjectProvider<com.pulse.local.repository.LocalStockEntryRepository> localStockProvider;
    private final String cartoMapKey;

    public MapApiController(GovernmentHospitalCatalogService catalog,
               HospitalRepository hospitals,
               MedicineRepository medicines,
               StockEntryRepository stock,
               ObjectProvider<LocalOfflineStore> localProvider,
               ObjectProvider<com.pulse.local.repository.LocalStockEntryRepository> localStockProvider,
               @Value("${pulse.map.carto-key:}") String cartoMapKey) {
        this.catalog = catalog;
        this.hospitals = hospitals;
        this.medicines = medicines;
        this.stock = stock;
        this.localProvider = localProvider;
        this.localStockProvider = localStockProvider;
        this.cartoMapKey = cartoMapKey == null ? "" : cartoMapKey.trim();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("cartoKey", cartoMapKey);
    }

    @GetMapping("/hospitals")
    public MapResponse hospitalMap(@RequestParam(defaultValue = "") String medicineId) {
        Long selectedMedicine = parseLong(medicineId);
        List<Medicine> medicineList = loadMedicines();
        Map<Long, Medicine> medicineById = medicineList.stream()
                .collect(Collectors.toMap(Medicine::getMedId, Function.identity(), (a,b)->a));

        Map<Long, Hospital> registered = loadHospitals();
        Map<String, Hospital> byKey = registered.values().stream()
                .filter(h -> h.getGovernmentHospitalKey() != null && !h.getGovernmentHospitalKey().isBlank())
                .collect(Collectors.toMap(Hospital::getGovernmentHospitalKey, Function.identity(), (a,b)->a));

        Map<Long, List<StockEntry>> cloudStock = new HashMap<>();
        Map<Long, List<LocalStockEntry>> localStock = new HashMap<>();
        var local = localProvider.getIfAvailable();
        var localRepo = localStockProvider.getIfAvailable();
        boolean localReady = local != null && localRepo != null && local.hasLocalData();
        if (!localReady) {
            try {
                for (StockEntry entry : stock.findAll()) {
                    cloudStock.computeIfAbsent(entry.getHospitalId(), k -> new ArrayList<>()).add(entry);
                }
            } catch (RuntimeException ignored) { }
        }
        if (localReady) {
            for (LocalStockEntry entry : localRepo.findAll()) {
                localStock.computeIfAbsent(entry.getHospitalId(), k -> new ArrayList<>()).add(entry);
            }
        }

        List<MapHospital> result = new ArrayList<>();
        Set<Long> representedHospitalIds = new HashSet<>();

        for (GovernmentHospitalCatalogService.GovernmentHospital g : catalog.all()) {
            Hospital h = byKey.get(g.key());
            if (h == null) h = findLegacyMatch(g, registered.values());
            boolean isRegistered = h != null;
            if (isRegistered) representedHospitalIds.add(h.getHospitalId());

            String status = g.closed() ? "CLOSED" : (isRegistered ? "CONNECTED" : "NOT_CONNECTED");
            int quantity = 0;
            int threshold = 0;
            boolean hasMedicineStock = false;

            if (isRegistered) {
                if (selectedMedicine != null) {
                    StockValue value = stockFor(h.getHospitalId(), selectedMedicine, medicineById, cloudStock, localStock);
                    if (value != null) {
                        hasMedicineStock = true;
                        quantity = value.quantity;
                        threshold = value.threshold;
                        status = StockStatus.from(quantity, threshold).name();
                    }
                } else {
                    List<StockValue> values = allStockFor(h.getHospitalId(), medicineById, cloudStock, localStock);
                    if (!values.isEmpty()) {
                        status = aggregate(values);
                        hasMedicineStock = true;
                    }
                }
            }

            result.add(new MapHospital(
                    g.key(), g.name(), g.district(), g.category(), g.latitude(), g.longitude(), g.address(),
                    g.closed(), isRegistered, status, quantity, threshold, hasMedicineStock, g.googleMapsUrl()));
        }

        // Also expose connected hospitals that already exist in Supabase but do not
        // yet have a government_hospital_key or do not exactly match the official
        // directory name. This keeps legacy/demo hospitals visible and lit up.
        for (Hospital h : registered.values()) {
            if (representedHospitalIds.contains(h.getHospitalId())) continue;
            if (h.getLatitude() == null || h.getLongitude() == null) continue;
            String key = h.getGovernmentHospitalKey();
            if (key == null || key.isBlank()) key = "registered-" + h.getHospitalId();
            String status = "CONNECTED";
            int quantity = 0, threshold = 0;
            boolean hasMedicineStock = false;
            if (selectedMedicine != null) {
                StockValue value = stockFor(h.getHospitalId(), selectedMedicine, medicineById, cloudStock, localStock);
                if (value != null) {
                    hasMedicineStock = true;
                    quantity = value.quantity;
                    threshold = value.threshold;
                    status = StockStatus.from(quantity, threshold).name();
                }
            } else {
                List<StockValue> values = allStockFor(h.getHospitalId(), medicineById, cloudStock, localStock);
                if (!values.isEmpty()) {
                    status = aggregate(values);
                    hasMedicineStock = true;
                }
            }
            result.add(new MapHospital(
                    key, h.getName(), h.getDistrict() == null ? "" : h.getDistrict(),
                    "Connected hospital", h.getLatitude(), h.getLongitude(),
                    h.getName(), false, true, status, quantity, threshold, hasMedicineStock,
                    "https://www.google.com/maps/search/?api=1&query=" + h.getLatitude() + "," + h.getLongitude()));
        }

        String medicineName = selectedMedicine == null ? "" :
                medicineById.getOrDefault(selectedMedicine, null) == null ? "" : medicineById.get(selectedMedicine).getName();
        return new MapResponse(result, medicineName, selectedMedicine);
    }

    @GetMapping("/medicines")
    public List<Map<String,Object>> medicines() {
        return loadMedicines().stream().map(m -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("medicineId", m.getMedId());
            row.put("name", m.getName());
            row.put("category", m.getCat());
            return row;
        }).toList();
    }

    private List<Medicine> loadMedicines() {
        LocalOfflineStore local = localProvider.getIfAvailable();
        if (local != null && local.hasLocalData()) return local.medicines();
        try { return medicines.findAll(); }
        catch (RuntimeException ex) { return local == null ? List.of() : local.medicines(); }
    }

    private Map<Long, Hospital> loadHospitals() {
        // Prefer the live Supabase rows when reachable so existing registered
        // hospitals (including legacy rows without a government key) stay lit.
        try {
            Map<Long, Hospital> cloud = hospitals.findMapRows().stream().map(this::toHospital)
                    .collect(Collectors.toMap(Hospital::getHospitalId, Function.identity(), (a,b)->a));
            if (!cloud.isEmpty()) return cloud;
        } catch (RuntimeException ignored) { }

        LocalOfflineStore local = localProvider.getIfAvailable();
        if (local != null && local.hasLocalData()) {
            return local.hospitals().stream().collect(Collectors.toMap(Hospital::getHospitalId, Function.identity(), (a,b)->a));
        }
        return Map.of();
    }

    private Hospital toHospital(HospitalRepository.HospitalMapRow row) {
        Hospital h = new Hospital(row.getHospitalId(), row.getName(), row.getDistrict());
        h.setDistrictId(row.getDistrictId());
        h.setGovernmentHospitalKey(row.getGovernmentHospitalKey());
        h.setLatitude(row.getLatitude());
        h.setLongitude(row.getLongitude());
        return h;
    }

    private Hospital findLegacyMatch(GovernmentHospitalCatalogService.GovernmentHospital g, Collection<Hospital> values) {
        String target = normalize(g.name());
        for (Hospital h : values) {
            if (h.getDistrict() == null || !g.district().equalsIgnoreCase(h.getDistrict())) continue;
            String candidate = normalize(h.getName());
            if (candidate.equals(target)) return h;
            if (candidate.contains("ernakulam") && target.contains("ernakulam")
                    && candidate.contains("generalhospital") && target.contains("generalhospital")) return h;
            if (candidate.contains("kozhikode") && target.contains("kozhikode")
                    && candidate.contains("generalhospital") && target.contains("generalhospital")) return h;
        }
        return null;
    }

    private StockValue stockFor(Long hospitalId, Long medicineId, Map<Long, Medicine> medicineById,
                                Map<Long, List<StockEntry>> cloud, Map<Long, List<LocalStockEntry>> local) {
        for (StockEntry e : cloud.getOrDefault(hospitalId, List.of())) {
            if (medicineId.equals(e.getMedId())) {
                Medicine m = medicineById.get(medicineId);
                if (m != null) return new StockValue(e.getQuantity(), m.getThreshold());
            }
        }
        for (LocalStockEntry e : local.getOrDefault(hospitalId, List.of())) {
            if (medicineId.equals(e.getMedicineId())) {
                Medicine m = medicineById.get(medicineId);
                if (m != null) return new StockValue(e.getQuantity(), m.getThreshold());
            }
        }
        return null;
    }

    private List<StockValue> allStockFor(Long hospitalId, Map<Long, Medicine> medicineById,
                                          Map<Long, List<StockEntry>> cloud, Map<Long, List<LocalStockEntry>> local) {
        List<StockValue> values = new ArrayList<>();
        for (StockEntry e : cloud.getOrDefault(hospitalId, List.of())) {
            Medicine m = medicineById.get(e.getMedId());
            if (m != null) values.add(new StockValue(e.getQuantity(), m.getThreshold()));
        }
        if (values.isEmpty()) {
            for (LocalStockEntry e : local.getOrDefault(hospitalId, List.of())) {
                Medicine m = medicineById.get(e.getMedicineId());
                if (m != null) values.add(new StockValue(e.getQuantity(), m.getThreshold()));
            }
        }
        return values;
    }

    private String aggregate(List<StockValue> values) {
        boolean anyGreen = false, anyYellow = false, anyRed = false;
        for (StockValue v : values) {
            switch (StockStatus.from(v.quantity, v.threshold)) {
                case GREEN -> anyGreen = true;
                case YELLOW -> anyYellow = true;
                case RED -> anyRed = true;
            }
        }
        if (anyRed && !anyYellow && !anyGreen) return "RED";
        if (anyYellow || (anyRed && anyGreen)) return "YELLOW";
        return anyGreen ? "GREEN" : "UNKNOWN";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "").trim();
    }

    private static Long parseLong(String value) {
        try { return value == null || value.isBlank() ? null : Long.valueOf(value); }
        catch (NumberFormatException ex) { return null; }
    }

    private record StockValue(int quantity, int threshold) {}

    public record MapResponse(List<MapHospital> hospitals, String medicineName, Long medicineId) {}

    public record MapHospital(String key, String name, String district, String category, double latitude, double longitude,
                              String address, boolean closed, boolean registered, String stockStatus,
                              int quantity, int threshold, boolean hasMedicineStock, String googleMapsUrl) {}
}
