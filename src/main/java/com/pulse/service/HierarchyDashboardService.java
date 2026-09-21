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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class HierarchyDashboardService {
    private final AlertRepository alerts;
    private final HospitalRepository hospitals;
    private final MedicineRepository medicines;
    private final StockEntryRepository stock;
    private final UserRepository users;

    public HierarchyDashboardService(AlertRepository alerts, HospitalRepository hospitals,
                                     MedicineRepository medicines, StockEntryRepository stock,
                                     UserRepository users) {
        this.alerts = alerts;
        this.hospitals = hospitals;
        this.medicines = medicines;
        this.stock = stock;
        this.users = users;
    }

    public Snapshot snapshot(Collection<Hospital> scopeHospitals) {
        Set<Long> hospitalIds = new HashSet<>();
        scopeHospitals.forEach(h -> hospitalIds.add(h.getHospitalId()));

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
            rows.add(new Row(h.getName(), h.getDistrict(), m.getName(), m.getCat(),
                    e.getQuantity(), m.getThreshold(), status.name(), e.getLastUpdated()));
            units += e.getQuantity();
            medicineTypes.add(m.getMedId());
            if (e.getQuantity() == 0) out++;
            if (e.getQuantity() <= m.getThreshold()) low++;
        }

        rows.sort(Comparator.comparing(Row::hospitalName).thenComparing(Row::medicineName));

        List<Alert> activeAlerts = alerts.findByResolvedFalseOrderByCreatedAtDesc().stream()
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

        return new Snapshot(rows, activeAlerts, recent, nearExpiry, scopedUsers,
                units, medicineTypes.size(), scopeHospitals.size(), activeAlerts.size(), low, out);
    }

    public List<Hospital> allHospitals() { return hospitals.findAll(Sort.by("name")); }

    public List<Hospital> hospitalsInDistrict(String district) {
        return hospitals.findAll(Sort.by("name")).stream()
                .filter(h -> h.getDistrict() != null && h.getDistrict().equalsIgnoreCase(district))
                .toList();
    }

    public record Snapshot(List<Row> rows, List<Alert> activeAlerts, List<StockEntry> recentActivity,
                           List<Medicine> nearExpiry, List<User> scopedUsers,
                           int totalUnits, int medicineTypes, int hospitals,
                           int activeAlertsCount, int lowStockItems, int outOfStockItems) {}

    public record Row(String hospitalName, String district, String medicineName, String category,
                      int quantity, int threshold, String status, LocalDate lastUpdated) {}
}
