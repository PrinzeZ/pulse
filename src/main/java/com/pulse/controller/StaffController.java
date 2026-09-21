package com.pulse.controller;

import com.pulse.SessionSecurity;
import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.local.service.StockSyncService;
import com.pulse.model.Medicine;
import com.pulse.model.PharmacyStaff;
import com.pulse.repository.MedicineRepository;
import com.pulse.service.StaffService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class StaffController {

    private final SessionSecurity sessionSecurity;
    private final StaffService staffService;
    private final MedicineRepository medicineRepository;
    private final ObjectProvider<StockSyncService> stockSyncServiceProvider;
    private final ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public StaffController(SessionSecurity sessionSecurity,
                           StaffService staffService,
                           MedicineRepository medicineRepository,
                           ObjectProvider<StockSyncService> stockSyncServiceProvider,
                           ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider,
                           ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.sessionSecurity = sessionSecurity;
        this.staffService = staffService;
        this.medicineRepository = medicineRepository;
        this.stockSyncServiceProvider = stockSyncServiceProvider;
        this.localStockRepositoryProvider = localStockRepositoryProvider;
        this.localStoreProvider = localStoreProvider;
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
            // The hospital scope comes from the authenticated staff session.
            // entryId is intentionally not used for authorization because the
            // local H2 entry id and cloud entry id are different namespaces.
            Medicine medicine = findMedicineForStaff(medicineId);
            if (medicine == null) {
                redirectAttributes.addFlashAttribute("error",
                        "This medicine is not available in the local hospital data.");
                return "redirect:/staff/stock";
            }

            staffService.updateStock(
                    staff.getHospitalId(),
                    medicineId,
                    quantity,
                    medicine);

            if (localStockRepositoryProvider.getIfAvailable() != null) {
                redirectAttributes.addFlashAttribute(
                        "success",
                        "Stock updated locally. It will sync when the connection is restored.");
            } else {
                redirectAttributes.addFlashAttribute(
                        "success",
                        "Stock updated and synchronized.");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "The stock could not be saved locally. Please try again.");
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
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Local synchronization is not enabled.");
            return "redirect:/staff/stock";
        }

        StockSyncService.SyncResult result = syncService.syncHospital(staff.getHospitalId());

        if (result.status().startsWith("OFFLINE:")) {
            redirectAttributes.addFlashAttribute(
                    "syncStatus",
                    "Cloud unavailable. Your local changes are safe and will sync when the connection is restored. Pending: "
                            + result.pending());
        } else {
            redirectAttributes.addFlashAttribute(
                    "syncStatus",
                    "Sync complete. Pushed " + result.pushed()
                            + " local change(s), pulled " + result.pulled()
                            + " cloud record(s). Pending: " + result.pending());
        }

        return "redirect:/staff/local-stock";
    }

    @GetMapping("/staff/local-stock")
    public String localStock(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }

        LocalStockEntryRepository localRepository = localStockRepositoryProvider.getIfAvailable();
        LocalOfflineStore localStore = localStoreProvider.getIfAvailable();

        if (localRepository == null || localStore == null) {
            model.addAttribute("localSyncEnabled", false);
            return "staff-local-stock";
        }

        Long hospitalId = staff.getHospitalId();
        List<LocalStockEntry> entries = localRepository.findByHospitalId(hospitalId);
        List<LocalStockRow> rows = entries.stream()
                .map(entry -> new LocalStockRow(
                        entry,
                        localStore.findMedicine(entry.getMedicineId()).orElse(null)))
                .toList();

        long pending = entries.stream().filter(entry -> !entry.isSynced()).count();

        model.addAttribute("localSyncEnabled", true);
        model.addAttribute("localRows", rows);
        model.addAttribute("totalCount", entries.size());
        model.addAttribute("syncedCount", entries.size() - pending);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("hospitalId", hospitalId);
        return "staff-local-stock";
    }

    private Medicine findMedicineForStaff(Long medicineId) {
        LocalOfflineStore localStore = localStoreProvider.getIfAvailable();

        // In local mode, H2 is authoritative. Do not touch PostgreSQL first.
        if (localStore != null) {
            return localStore.findMedicine(medicineId).orElse(null);
        }

        return medicineRepository.findById(medicineId).orElse(null);
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
