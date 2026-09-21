package com.pulse.controller;

import com.pulse.SessionSecurity;
import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.local.service.StockSyncService;
import com.pulse.model.Medicine;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.StockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.service.StaffService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class StaffController {

    private final SessionSecurity sessionSecurity;
    private final StaffService staffService;
    private final StockEntryRepository stockEntryRepository;
    private final MedicineRepository medicineRepository;
    private final ObjectProvider<StockSyncService> stockSyncServiceProvider;
    private final ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider;

    public StaffController(SessionSecurity sessionSecurity,
                           StaffService staffService,
                           StockEntryRepository stockEntryRepository,
                           MedicineRepository medicineRepository,
                           ObjectProvider<StockSyncService> stockSyncServiceProvider,
                           ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider) {
        this.sessionSecurity = sessionSecurity;
        this.staffService = staffService;
        this.stockEntryRepository = stockEntryRepository;
        this.medicineRepository = medicineRepository;
        this.stockSyncServiceProvider = stockSyncServiceProvider;
        this.localStockRepositoryProvider = localStockRepositoryProvider;
    }

    @GetMapping("/staff/dashboard")
    public String dashboard(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }
        StaffService.StaffInventory inventory = staffService.getInventory(staff.getHospitalId());
        if (inventory == null) {
            return "redirect:/login";
        }
        model.addAttribute("inventory", inventory);
        addLocalSyncSummary(staff.getHospitalId(), model);
        return "staff_dashboard";
    }

    @GetMapping("/staff/stock")
    public String stockPage(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }
        StaffService.StaffInventory inventory = staffService.getInventory(staff.getHospitalId());
        if (inventory == null) {
            return "redirect:/login";
        }
        model.addAttribute("inventory", inventory);
        addLocalSyncSummary(staff.getHospitalId(), model);
        return "staff-stock";
    }

    @GetMapping("/staff/local-stock")
    public String localStockPage(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }

        LocalStockEntryRepository localRepository = localStockRepositoryProvider.getIfAvailable();
        if (localRepository == null) {
            model.addAttribute("localSyncEnabled", false);
            return "staff-local-stock";
        }

        List<LocalStockEntry> entries = localRepository.findByHospitalId(staff.getHospitalId()).stream()
                .sorted(Comparator.comparing(LocalStockEntry::getMedicineId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        Map<Long, Medicine> medicines = medicineRepository.findAll().stream()
                .collect(Collectors.toMap(Medicine::getMedId, Function.identity(), (a, b) -> a));

        List<LocalStockRow> rows = entries.stream()
                .map(entry -> new LocalStockRow(entry, medicines.get(entry.getMedicineId())))
                .toList();

        long syncedCount = entries.stream().filter(LocalStockEntry::isSynced).count();
        long pendingCount = entries.size() - syncedCount;

        model.addAttribute("localSyncEnabled", true);
        model.addAttribute("localRows", rows);
        model.addAttribute("syncedCount", syncedCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("totalCount", entries.size());
        model.addAttribute("hospitalId", staff.getHospitalId());
        return "staff-local-stock";
    }

    @PostMapping("/staff/stock/update")
    public String updateStock(HttpSession session,
                              @RequestParam(required = false) Long entryId,
                              @RequestParam Long medicineId,
                              @RequestParam int quantity,
                              RedirectAttributes redirectAttributes) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }

        try {
            if (entryId != null) {
                StockEntry existing = stockEntryRepository.findById(entryId).orElse(null);
                if (existing == null || !staff.getHospitalId().equals(existing.getHospitalId())) {
                    return "redirect:/staff/stock?error=true";
                }
            }

            Medicine medicine = medicineRepository.findById(medicineId).orElse(null);
            staffService.updateStock(staff.getHospitalId(), medicineId, quantity, medicine);
            redirectAttributes.addFlashAttribute("success", "Stock updated successfully.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Unable to update stock.");
        }
        return "redirect:/staff/stock";
    }

    @PostMapping("/staff/sync")
    public String sync(HttpSession session, RedirectAttributes redirectAttributes) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }
        StockSyncService syncService = stockSyncServiceProvider.getIfAvailable();
        if (syncService == null) {
            redirectAttributes.addFlashAttribute("error", "Local sync is not enabled.");
            return "redirect:/staff/stock";
        }
        StockSyncService.SyncResult result = syncService.syncHospital(staff.getHospitalId());
        redirectAttributes.addFlashAttribute("syncStatus",
                "Sync: " + result.status() + " | pushed=" + result.pushed() +
                        ", pulled=" + result.pulled() + ", pending=" + result.pending());
        return "redirect:/staff/local-stock";
    }

    private void addLocalSyncSummary(Long hospitalId, Model model) {
        LocalStockEntryRepository localRepository = localStockRepositoryProvider.getIfAvailable();
        if (localRepository == null) {
            model.addAttribute("localSyncEnabled", false);
            return;
        }
        List<LocalStockEntry> entries = localRepository.findByHospitalId(hospitalId);
        long pending = entries.stream().filter(entry -> !entry.isSynced()).count();
        model.addAttribute("localSyncEnabled", true);
        model.addAttribute("localTotalCount", entries.size());
        model.addAttribute("localSyncedCount", entries.size() - pending);
        model.addAttribute("localPendingCount", pending);
    }

    public record LocalStockRow(LocalStockEntry entry, Medicine medicine) {}
}
