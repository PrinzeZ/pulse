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
import com.pulse.service.StockTransferService;
import com.pulse.service.StockLedgerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;

import java.util.List;

@Controller
public class StaffController {

    private final SessionSecurity sessionSecurity;
    private final StaffService staffService;
    private final MedicineRepository medicineRepository;
    private final ObjectProvider<StockSyncService> stockSyncServiceProvider;
    private final ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final StockTransferService transferService;
    private final StockLedgerService ledgerService;
    private final com.pulse.service.AuditArchiveService auditArchiveService;

    public StaffController(SessionSecurity sessionSecurity,
                           StaffService staffService,
                           MedicineRepository medicineRepository,
                           ObjectProvider<StockSyncService> stockSyncServiceProvider,
                           ObjectProvider<LocalStockEntryRepository> localStockRepositoryProvider,
                           ObjectProvider<LocalOfflineStore> localStoreProvider,
                           StockTransferService transferService,
                           StockLedgerService ledgerService,
                           com.pulse.service.AuditArchiveService auditArchiveService) {
        this.sessionSecurity = sessionSecurity;
        this.staffService = staffService;
        this.medicineRepository = medicineRepository;
        this.stockSyncServiceProvider = stockSyncServiceProvider;
        this.localStockRepositoryProvider = localStockRepositoryProvider;
        this.localStoreProvider = localStoreProvider;
        this.transferService = transferService;
        this.ledgerService = ledgerService;
        this.auditArchiveService = auditArchiveService;
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
                    medicine,
                    staff.getUsername());

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

    @GetMapping("/staff/transfers")
    public String transfers(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) return "redirect:/login";
        model.addAttribute("transfers", transferService.hospitalViews(staff.getHospitalId()));
        model.addAttribute("hospitalId", staff.getHospitalId());
        return "staff-transfers";
    }

    @PreAuthorize("@pulseScope.canDispatchTransfer(#id)")
    @PostMapping("/staff/transfers/{id}/dispatch")
    public String dispatchTransfer(@org.springframework.web.bind.annotation.PathVariable Long id,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) return "redirect:/login";
        try {
            transferService.dispatch(id, staff.getHospitalId(), staff.getUsername());
            redirectAttributes.addFlashAttribute("success", "Transfer dispatched. Source stock was reduced locally and queued for synchronization.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "The transfer could not be dispatched.");
        }
        return "redirect:/staff/transfers";
    }

    @PreAuthorize("@pulseScope.canReceiveTransfer(#id)")
    @PostMapping("/staff/transfers/{id}/receive")
    public String receiveTransfer(@org.springframework.web.bind.annotation.PathVariable Long id,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) return "redirect:/login";
        try {
            transferService.receive(id, staff.getHospitalId(), staff.getUsername());
            redirectAttributes.addFlashAttribute("success", "Transfer received. Destination stock was increased locally and queued for synchronization.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "The transfer could not be received.");
        }
        return "redirect:/staff/transfers";
    }

    @GetMapping("/staff/audit")
    public String audit(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) return "redirect:/login";
        model.addAttribute("hospitalId", staff.getHospitalId());
        model.addAttribute("movements", auditArchiveService.recentForStaff(staff.getHospitalId()));
        model.addAttribute("verification", ledgerService.verifyCloud(staff.getHospitalId()));
        model.addAttribute("hotDays", auditArchiveService.getHotDays());
        model.addAttribute("archives", auditArchiveService.archives(staff.getHospitalId()));
        model.addAttribute("accessRequests", auditArchiveService.requestsForStaff(staff.getHospitalId(), staff.getUsername()));
        model.addAttribute("approvedHistorical", false);
        model.addAttribute("periodStart", LocalDate.now().minusDays(auditArchiveService.getHotDays() - 1L));
        model.addAttribute("periodEnd", LocalDate.now());
        model.addAttribute("quickDates", java.util.stream.IntStream.range(0, 30).mapToObj(i -> LocalDate.now().minusDays(i)).toList());
        return "staff-audit";
    }

    @GetMapping("/staff/audit/view")
    public String viewAudit(@RequestParam LocalDate start, @RequestParam LocalDate end,
                            HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) return "redirect:/login";
        if (end.isBefore(start)) return "redirect:/staff/audit";
        LocalDate cutoff = LocalDate.now().minusDays(auditArchiveService.getHotDays() - 1L);
        boolean hotWindow = !start.isBefore(cutoff);
        if (!hotWindow && !auditArchiveService.approvedRequest(staff.getHospitalId(), staff.getUsername(), start, end)) {
            return "redirect:/access-denied";
        }
        model.addAttribute("title", "Audit spreadsheet");
        model.addAttribute("subtitle", hotWindow
                ? "Read-only browser view. Staff cannot download or edit the workbook."
                : "Administrator-approved historical period. Read-only browser view.");
        model.addAttribute("rows", auditArchiveService.reportRows(staff.getHospitalId(), start, end, true));
        model.addAttribute("medicineRows", List.of());
        model.addAttribute("medicineName", null);
        model.addAttribute("medicineId", null);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("canDownload", false);
        model.addAttribute("adminView", false);
        model.addAttribute("quickDates", java.util.stream.IntStream.range(0, 30).mapToObj(i -> LocalDate.now().minusDays(i)).toList());
        return "audit-preview";
    }

    @PostMapping("/staff/audit/request")
    public String requestAuditArchive(HttpSession session, @RequestParam LocalDate start, @RequestParam LocalDate end,
                                      @RequestParam(required = false, defaultValue = "Historical stock audit review") String reason,
                                      RedirectAttributes redirectAttributes) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) return "redirect:/login";
        try {
            auditArchiveService.requestArchiveAccess(staff.getHospitalId(), staff.getUsername(), start, end, reason);
            redirectAttributes.addFlashAttribute("success", "Audit access request sent to your hospital administrator.");
        } catch (RuntimeException ex) { redirectAttributes.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/staff/audit";
    }

    @GetMapping("/staff/audit/approved")
    public String approvedAudit(HttpSession session, @RequestParam LocalDate start, @RequestParam LocalDate end, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) return "redirect:/login";
        if (!auditArchiveService.approvedRequest(staff.getHospitalId(), staff.getUsername(), start, end)) return "redirect:/access-denied";
        model.addAttribute("hospitalId", staff.getHospitalId());
        model.addAttribute("movements", auditArchiveService.archivedHistory(staff.getHospitalId(), start, end));
        model.addAttribute("periodStart", start);
        model.addAttribute("periodEnd", end);
        model.addAttribute("accessRequests", auditArchiveService.requestsForStaff(staff.getHospitalId(), staff.getUsername()));
        model.addAttribute("approvedHistorical", true);
        model.addAttribute("quickDates", java.util.stream.IntStream.range(0, 30).mapToObj(i -> LocalDate.now().minusDays(i)).toList());
        return "staff-audit";
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
