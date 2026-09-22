package com.pulse.service;

import com.pulse.model.Hospital;
import com.pulse.model.MedicineRequestStatus;
import com.pulse.model.StockTransferStatus;
import com.pulse.repository.MedicineRequestRepository;
import com.pulse.repository.StockTransferRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsService {
    private final HierarchyDashboardService dashboard;
    private final MedicineRequestRepository requests;
    private final StockTransferRepository transfers;

    public AnalyticsService(HierarchyDashboardService dashboard, MedicineRequestRepository requests, StockTransferRepository transfers) {
        this.dashboard = dashboard; this.requests = requests; this.transfers = transfers;
    }

    public AnalyticsView forScope(Collection<Hospital> hospitals, HierarchyDashboardService.ScopeLevel level) {
        var snapshot = dashboard.snapshot(hospitals, level);
        Set<Long> ids = hospitals.stream().map(Hospital::getHospitalId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        int pendingRequests = 0, activeTransfers = 0, completedTransfers = 0;
        for (var r : requests.findAll()) {
            if (!ids.contains(r.getHospitalId())) continue;
            if (r.getStatus() == MedicineRequestStatus.PENDING_DISTRICT || r.getStatus() == MedicineRequestStatus.UNDER_REVIEW || r.getStatus() == MedicineRequestStatus.ESCALATED_TO_STATE) pendingRequests++;
        }
        for (var t : transfers.findAll()) {
            if (!ids.contains(t.getSourceHospitalId()) && !ids.contains(t.getDestinationHospitalId())) continue;
            if (t.getStatus() == StockTransferStatus.CREATED || t.getStatus() == StockTransferStatus.APPROVED || t.getStatus() == StockTransferStatus.DISPATCHED || t.getStatus() == StockTransferStatus.IN_TRANSIT) activeTransfers++;
            if (t.getStatus() == StockTransferStatus.RECEIVED) completedTransfers++;
        }
        List<HospitalMetric> hospitalMetrics = new ArrayList<>();
        for (Hospital h : hospitals) {
            var rows = snapshot.rows().stream().filter(r -> Objects.equals(r.hospitalId(), h.getHospitalId())).toList();
            int units = rows.stream().mapToInt(HierarchyDashboardService.Row::quantity).sum();
            int low = (int) rows.stream().filter(r -> !"GREEN".equals(r.status())).count();
            int red = (int) rows.stream().filter(r -> "RED".equals(r.status())).count();
            hospitalMetrics.add(new HospitalMetric(h.getHospitalId(), h.getName(), h.getDistrict(), units, low, red));
        }
        hospitalMetrics.sort(Comparator.comparingInt(HospitalMetric::redItems).reversed().thenComparing(HospitalMetric::hospitalName));
        return new AnalyticsView(snapshot.hospitals(), snapshot.totalUnits(), snapshot.medicineTypes(), snapshot.lowStockItems(), snapshot.outOfStockItems(), snapshot.activeAlertsCount(), pendingRequests, activeTransfers, completedTransfers, hospitalMetrics, snapshot.scopeSignals());
    }

    public record AnalyticsView(int hospitals, int totalUnits, int medicineTypes, int lowStockItems, int outOfStockItems, int activeAlerts, int pendingRequests, int activeTransfers, int completedTransfers, List<HospitalMetric> hospitalsByRisk, List<HierarchyDashboardService.ScopeSignal> signals) {}
    public record HospitalMetric(Long hospitalId, String hospitalName, String district, int units, int lowItems, int redItems) {}
}
