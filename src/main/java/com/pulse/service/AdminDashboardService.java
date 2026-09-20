package com.pulse.service;

import com.pulse.model.Alert;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.model.StockStatus;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminDashboardService {

    private final AlertRepository alertRepository;
    private final HospitalRepository hospitalRepository;
    private final MedicineRepository medicineRepository;
    private final StockEntryRepository stockEntryRepository;

    public AdminDashboardService(AlertRepository alertRepository,
                                 HospitalRepository hospitalRepository,
                                 MedicineRepository medicineRepository,
                                 StockEntryRepository stockEntryRepository) {
        this.alertRepository = alertRepository;
        this.hospitalRepository = hospitalRepository;
        this.medicineRepository = medicineRepository;
        this.stockEntryRepository = stockEntryRepository;
    }

    public DashboardSnapshot getDashboard() {
        List<Alert> activeAlerts = alertRepository.findByResolvedFalseOrderByCreatedAtDesc();
        List<StockEntry> allStock = stockEntryRepository.findAll();
        List<StockEntry> recentActivity = stockEntryRepository.findAll(
                Sort.by(Sort.Direction.DESC, "lastUpdated", "entryId"));
        List<Hospital> hospitals = hospitalRepository.findAll();
        List<Medicine> medicines = medicineRepository.findAll();

        Map<Long, Hospital> hospitalsById = new LinkedHashMap<>();
        hospitals.forEach(hospital -> hospitalsById.put(hospital.getHospitalId(), hospital));
        Map<Long, Medicine> medicinesById = new LinkedHashMap<>();
        medicines.forEach(medicine -> medicinesById.put(medicine.getMedId(), medicine));

        List<GlobalStockRow> globalStock = new ArrayList<>();
        List<StockEntry> criticalShortages = new ArrayList<>();
        int totalUnits = 0;
        int lowStockCount = 0;
        int outOfStockCount = 0;
        Set<Long> representedHospitals = new HashSet<>();
        Set<Long> representedMedicines = new HashSet<>();

        for (StockEntry entry : allStock) {
            Hospital hospital = hospitalsById.get(entry.getHospitalId());
            Medicine medicine = medicinesById.get(entry.getMedId());
            if (hospital == null || medicine == null) {
                continue;
            }
            StockStatus status = StockStatus.from(entry.getQuantity(), medicine.getThreshold());
            globalStock.add(new GlobalStockRow(
                    hospital.getName(),
                    hospital.getDistrict(),
                    medicine.getName(),
                    medicine.getCat(),
                    entry.getQuantity(),
                    medicine.getThreshold(),
                    status.name(),
                    entry.getLastUpdated()));
            totalUnits += entry.getQuantity();
            representedHospitals.add(hospital.getHospitalId());
            representedMedicines.add(medicine.getMedId());
            if (entry.getQuantity() == 0) {
                outOfStockCount++;
            }
            if (entry.getQuantity() < medicine.getThreshold()) {
                lowStockCount++;
                criticalShortages.add(entry);
            }
        }

        globalStock.sort(Comparator.comparing(GlobalStockRow::hospitalName)
                .thenComparing(GlobalStockRow::medicineName));
        criticalShortages.sort(Comparator.comparingInt(StockEntry::getQuantity)
                .thenComparing(StockEntry::getHospitalId)
                .thenComparing(StockEntry::getMedId));

        DashboardTotals totals = new DashboardTotals(
                totalUnits,
                representedMedicines.size(),
                representedHospitals.size(),
                activeAlerts.size(),
                lowStockCount,
                outOfStockCount);

        return new DashboardSnapshot(
                activeAlerts,
                globalStock,
                recentActivity,
                criticalShortages,
                List.of(),
                List.of(),
                totals,
                false,
                false);
    }

    public record DashboardSnapshot(List<Alert> activeAlerts,
                                    List<GlobalStockRow> globalStock,
                                    List<StockEntry> recentActivity,
                                    List<StockEntry> criticalShortages,
                                    List<Object> nearExpiryItems,
                                    List<Object> popularSearches,
                                    DashboardTotals totals,
                                    boolean nearExpirySupported,
                                    boolean popularSearchesSupported) {
    }

    public record DashboardTotals(int totalUnits,
                                  int medicineTypes,
                                  int hospitalsRepresented,
                                  int activeAlerts,
                                  int lowStockItems,
                                  int outOfStockItems) {
    }

    public record GlobalStockRow(String hospitalName,
                                 String district,
                                 String medicineName,
                                 String category,
                                 int quantity,
                                 int threshold,
                                 String status,
                                 java.time.LocalDate lastUpdated) {
    }
}
