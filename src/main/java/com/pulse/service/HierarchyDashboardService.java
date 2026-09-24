package com.pulse.service;

import com.pulse.model.Alert;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.model.StockStatus;
import com.pulse.model.User;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.repository.UserRepository;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.local.model.LocalStockEntry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class HierarchyDashboardService {
    private final AlertRepository alerts;
    private final HospitalRepository hospitals;
    private final MedicineRepository medicines;
    private final StockEntryRepository stock;
    private final UserRepository users;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final AlertService alertService;

    public HierarchyDashboardService(AlertRepository alerts, HospitalRepository hospitals,
                                     MedicineRepository medicines, StockEntryRepository stock,
                                     UserRepository users, ObjectProvider<LocalOfflineStore> localStoreProvider, AlertService alertService) {
        this.alerts = alerts;
        this.hospitals = hospitals;
        this.medicines = medicines;
        this.stock = stock;
        this.users = users;
        this.localStoreProvider = localStoreProvider;
        this.alertService = alertService;
    }

    public Snapshot snapshot(Collection<Hospital> scopeHospitals) {
        return snapshot(scopeHospitals, ScopeLevel.HOSPITAL);
    }

    public Snapshot snapshot(Collection<Hospital> scopeHospitals, ScopeLevel scopeLevel) {
        try {
            return cloudSnapshot(scopeHospitals, scopeLevel);
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            if (local == null) throw ex;
            return localSnapshot(scopeHospitals, scopeLevel, local);
        }
    }

    private Snapshot cloudSnapshot(Collection<Hospital> scopeHospitals, ScopeLevel scopeLevel) {
        Set<Long> hospitalIds = scopeHospitals.stream()
                .map(Hospital::getHospitalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Hospital> hospitalMap = new HashMap<>();
        scopeHospitals.forEach(h -> hospitalMap.put(h.getHospitalId(), h));
        Map<Long, Medicine> medicineMap = new HashMap<>();
        medicines.findAll().forEach(m -> medicineMap.put(m.getMedId(), m));

        List<Row> rows = new ArrayList<>();
        int units = 0, low = 0, out = 0;
        Set<Long> medicineTypes = new HashSet<>();

        for (StockEntry e : stock.findAll()) {
            if (!hospitalIds.contains(e.getHospitalId())) continue;
            Hospital h = hospitalMap.get(e.getHospitalId());
            Medicine m = medicineMap.get(e.getMedId());
            if (h == null || m == null) continue;
            StockStatus status = StockStatus.from(e.getQuantity(), m.getThreshold());
            rows.add(new Row(h.getHospitalId(), h.getName(), h.getDistrict(), m.getName(), m.getCat(),
                    e.getQuantity(), m.getThreshold(), status.name(), e.getLastUpdated()));
            units += e.getQuantity();
            medicineTypes.add(m.getMedId());
            if (e.getQuantity() == 0) out++;
            if (e.getQuantity() <= m.getThreshold()) low++;
        }

        rows.sort(Comparator.comparing(Row::hospitalName).thenComparing(Row::medicineName));

        List<Alert> activeAlerts = alertService.getActiveAlerts().stream()
                .filter(a -> hospitalIds.contains(a.getHospitalId())).toList();
        List<StockEntry> recent = stock.findAll(Sort.by(Sort.Direction.DESC, "lastUpdated", "entryId"))
                .stream().filter(e -> hospitalIds.contains(e.getHospitalId())).limit(8).toList();
        Set<Long> scopedMedicineIds = new HashSet<>();
        for (StockEntry e : stock.findAll()) {
            if (hospitalIds.contains(e.getHospitalId())) scopedMedicineIds.add(e.getMedId());
        }
        List<Medicine> nearExpiry = medicines.findAll().stream()
                .filter(m -> scopedMedicineIds.contains(m.getMedId()))
                .filter(m -> m.getExpiryDate() != null && m.getExpiryDate().isBefore(LocalDate.now().plusMonths(3)))
                .toList();
        List<User> scopedUsers = users.findAll().stream()
                .filter(u -> u.getHospitalId() != null && hospitalIds.contains(u.getHospitalId()))
                .toList();

        List<ScopeSignal> scopeSignals = buildScopeSignals(scopeHospitals, rows, scopeLevel);

        return new Snapshot(rows, activeAlerts, scopeSignals, recent, nearExpiry, scopedUsers,
                units, medicineTypes.size(), scopeHospitals.size(), activeAlerts.size(), low, out);
    }

    private List<ScopeSignal> buildScopeSignals(Collection<Hospital> scopeHospitals,
                                                     List<Row> rows,
                                                     ScopeLevel scopeLevel) {
        if (rows.isEmpty() || scopeHospitals.isEmpty()) return List.of();

        Map<Long, List<Row>> byHospital = rows.stream()
                .collect(Collectors.groupingBy(Row::hospitalId));

        // The hierarchy dashboards are exception dashboards, not inventory tables.
        // A hospital becomes "hospital-wide critical" only when every medicine line
        // represented for that hospital is at or below its critical threshold.
        Set<String> trackedMedicines = rows.stream()
                .map(Row::medicineName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> criticalHospitalIds = new HashSet<>();
        for (Hospital hospital : scopeHospitals) {
            List<Row> hospitalRows = byHospital.getOrDefault(
                    hospital.getHospitalId(), List.of());

            if (!trackedMedicines.isEmpty()
                    && hospitalRows.size() == trackedMedicines.size()
                    && hospitalRows.stream().allMatch(r -> "RED".equals(r.status()))) {
                criticalHospitalIds.add(hospital.getHospitalId());
            }
        }

        List<ScopeSignal> signals = new ArrayList<>();

        if (scopeLevel == ScopeLevel.DISTRICT) {
            // District admins only need exceptions: a hospital whose entire
            // tracked medicine position is critical.
            for (Hospital hospital : scopeHospitals) {
                if (!criticalHospitalIds.contains(hospital.getHospitalId())) continue;

                List<Row> hospitalRows = byHospital.getOrDefault(
                        hospital.getHospitalId(), List.of());

                signals.add(new ScopeSignal(
                        "RED",
                        "Hospital-wide critical stock",
                        hospital.getName() + " is at or below the critical threshold for all "
                                + hospitalRows.size() + " tracked medicine lines."
                ));
            }
        }

        if (scopeLevel == ScopeLevel.STATE) {
            // State admins receive district exceptions, not individual medicine
            // rows. A district is RED when at least half of its hospitals (and
            // at least one hospital) are hospital-wide critical.
            Map<String, List<Hospital>> hospitalsByDistrict = scopeHospitals.stream()
                    .filter(h -> h.getDistrict() != null)
                    .collect(Collectors.groupingBy(Hospital::getDistrict));

            for (Map.Entry<String, List<Hospital>> entry : hospitalsByDistrict.entrySet()) {
                List<Hospital> districtHospitals = entry.getValue();
                long criticalCount = districtHospitals.stream()
                        .filter(h -> criticalHospitalIds.contains(h.getHospitalId()))
                        .count();

                int requiredCriticalHospitals = Math.max(
                        1,
                        (int) Math.ceil(districtHospitals.size() * 0.50)
                );

                if (criticalCount >= requiredCriticalHospitals) {
                    signals.add(new ScopeSignal(
                            "RED",
                            "District requires attention",
                            entry.getKey() + " has " + criticalCount + " of "
                                    + districtHospitals.size()
                                    + " hospitals with hospital-wide critical stock."
                    ));
                }
            }
        }

        return signals.stream()
                .sorted(Comparator.comparing(ScopeSignal::title))
                .limit(8)
                .toList();
    }

    public List<Hospital> allHospitals() {
        try { return hospitals.findAll(Sort.by("name")); }
        catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            return local == null ? List.of() : local.hospitals();
        }
    }

    public List<Hospital> hospitalsInDistrict(String district) {
        try {
            return hospitals.findAll(Sort.by("name")).stream()
                    .filter(h -> h.getDistrict() != null && h.getDistrict().equalsIgnoreCase(district))
                    .toList();
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            if (local == null) throw ex;
            return local.hospitals().stream()
                    .filter(h -> h.getDistrict() != null && h.getDistrict().equalsIgnoreCase(district))
                    .sorted(Comparator.comparing(Hospital::getName))
                    .toList();
        }
    }

    private Snapshot localSnapshot(Collection<Hospital> requestedScope, ScopeLevel scopeLevel, LocalOfflineStore local) {
        List<Hospital> localHospitals = local.hospitals();
        Set<Long> ids = requestedScope.stream().map(Hospital::getHospitalId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return new Snapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0, 0, 0);

        Map<Long, Hospital> hospitalMap = localHospitals.stream().collect(Collectors.toMap(Hospital::getHospitalId, h -> h));
        Map<Long, Medicine> medicineMap = local.medicines().stream().collect(Collectors.toMap(Medicine::getMedId, m -> m));
        List<Row> rows = new ArrayList<>();
        List<StockEntry> recent = new ArrayList<>();
        int units = 0, low = 0, out = 0;
        Set<Long> medicineTypes = new HashSet<>();
        for (Long hospitalId : ids) {
            for (LocalStockEntry e : local.stockForHospital(hospitalId)) {
                Hospital h = hospitalMap.get(hospitalId); Medicine m = medicineMap.get(e.getMedicineId());
                if (h == null || m == null) continue;
                StockStatus status = StockStatus.from(e.getQuantity(), m.getThreshold());
                LocalDate date = null;
                try { if (e.getLastUpdated() != null) date = LocalDate.parse(e.getLastUpdated()); } catch (RuntimeException ignored) {}
                rows.add(new Row(hospitalId, h.getName(), h.getDistrict(), m.getName(), m.getCat(), e.getQuantity(), m.getThreshold(), status.name(), date));
                StockEntry se = new StockEntry(e.getCloudEntryId(), hospitalId, e.getMedicineId(), e.getQuantity()); se.setLastUpdated(date); recent.add(se);
                units += e.getQuantity(); medicineTypes.add(m.getMedId()); if (e.getQuantity() == 0) out++; if (e.getQuantity() <= m.getThreshold()) low++;
            }
        }
        rows.sort(Comparator.comparing(Row::hospitalName).thenComparing(Row::medicineName));
        recent.sort(Comparator.comparing(StockEntry::getLastUpdated, Comparator.nullsLast(Comparator.reverseOrder())));
        if (recent.size() > 8) recent = new ArrayList<>(recent.subList(0, 8));
        List<User> scopedUsers = ids.stream().flatMap(id -> local.staffForHospital(id).stream()).toList();
        List<Alert> activeAlerts = alertService.getActiveAlerts().stream()
                .filter(a -> ids.contains(a.getHospitalId())).toList();
        List<Medicine> nearExpiry = local.medicines().stream()
                .filter(m -> m.getExpiryDate() != null
                        && m.getExpiryDate().isBefore(LocalDate.now().plusMonths(3)))
                .toList();
        List<ScopeSignal> scopeSignals = buildScopeSignals(requestedScope, rows, scopeLevel);
        return new Snapshot(rows, activeAlerts, scopeSignals, recent, nearExpiry, scopedUsers,
                units, medicineTypes.size(), ids.size(), activeAlerts.size(), low, out);
    }

    public enum ScopeLevel { HOSPITAL, DISTRICT, STATE }

    public record Snapshot(List<Row> rows, List<Alert> activeAlerts, List<ScopeSignal> scopeSignals,
                           List<StockEntry> recentActivity, List<Medicine> nearExpiry, List<User> scopedUsers,
                           int totalUnits, int medicineTypes, int hospitals,
                           int activeAlertsCount, int lowStockItems, int outOfStockItems) {}

    public record ScopeSignal(String severity, String title, String detail) {}

    public record Row(Long hospitalId, String hospitalName, String district, String medicineName, String category,
                      int quantity, int threshold, String status, LocalDate lastUpdated) {}
}
