package com.pulse.controller;

import com.pulse.service.HierarchyDashboardService;
import com.pulse.service.StaffManagementService;
import com.pulse.service.StockLedgerService;
import com.pulse.service.MedicineRequestActionLogService;
import com.pulse.service.MedicineRequestAuditArchiveService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final HierarchyDashboardService dashboard;
    private final StaffManagementService staffManagement;
    private final StockLedgerService ledgerService;
    private final com.pulse.service.AuditArchiveService auditArchiveService;
    private final MedicineRequestActionLogService requestActionLogService;
    private final MedicineRequestAuditArchiveService requestDecisionArchives;

    public AdminController(HierarchyDashboardService dashboard, StaffManagementService staffManagement,
                           StockLedgerService ledgerService, com.pulse.service.AuditArchiveService auditArchiveService,
                           MedicineRequestActionLogService requestActionLogService,
                           MedicineRequestAuditArchiveService requestDecisionArchives) {
        this.dashboard = dashboard;
        this.staffManagement = staffManagement;
        this.ledgerService = ledgerService;
        this.auditArchiveService = auditArchiveService;
        this.requestActionLogService = requestActionLogService;
        this.requestDecisionArchives = requestDecisionArchives;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/staff")
    public String staff(HttpSession session, Model model) {
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) return "redirect:/login";
        model.addAttribute("staffMembers", staffManagement.staffForHospital(hospitalId));
        return "admin_staff";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/staff/create")
    public String createStaff(HttpSession session,
                              @RequestParam String name,
                              @RequestParam String username,
                              @RequestParam String password,
                              @RequestParam String confirmation,
                              @RequestParam(defaultValue = "false") boolean authorize,
                              RedirectAttributes redirectAttributes) {
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        Long stateId = (Long) session.getAttribute("stateId");
        Long districtId = (Long) session.getAttribute("districtId");
        if (hospitalId == null) return "redirect:/login";
        try {
            staffManagement.createStaff(hospitalId, stateId, districtId, name, username, password, confirmation, authorize);
            redirectAttributes.addFlashAttribute("success", authorize
                    ? "Staff account created and authorized. They can log in now."
                    : "Staff account created but login is not authorized yet.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @PreAuthorize("@pulseScope.canManageStaff(#id)")
    @PostMapping("/staff/{id}/authorization")
    public String setStaffAuthorization(@PathVariable Long id,
                                         @RequestParam boolean authorize,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) return "redirect:/login";
        try {
            staffManagement.setAuthorized(hospitalId, id, authorize);
            redirectAttributes.addFlashAttribute("success", authorize
                    ? "Staff login authorized."
                    : "Staff login authorization removed.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit")
    public String audit(HttpSession session, Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) return "redirect:/login";
        model.addAttribute("hospitalId", hospitalId);
        model.addAttribute("movements", auditArchiveService.history(hospitalId, LocalDate.now().minusDays(auditArchiveService.getHotDays() - 1L), LocalDate.now()));
        var localVerification = ledgerService.verifyLocal(hospitalId);
        model.addAttribute("verification", "LOCAL_LEDGER_DISABLED".equals(localVerification.message())
                ? ledgerService.verifyCloud(hospitalId) : localVerification);
        model.addAttribute("archives", auditArchiveService.archives(hospitalId));
        model.addAttribute("archiveRequests", auditArchiveService.pendingRequests(hospitalId));
        model.addAttribute("archiveEncryptionReady", auditArchiveService.archiveEncryptionReady());
        model.addAttribute("hotDays", auditArchiveService.getHotDays());
        model.addAttribute("coldAfterDays", auditArchiveService.getColdAfterDays());
        model.addAttribute("retentionDays", auditArchiveService.getRetentionDays());
        // The stock ledger and the administrative request-decision ledger are separate
        // tamper-evident trails, but both are shown here so a hospital administrator
        // has one read-only audit workspace for stock changes and request decisions.
        model.addAttribute("decisionLogs", requestActionLogService.hospitalViews(hospitalId));
        model.addAttribute("decisionArchivePeriods", requestDecisionArchives.periodsForHospitals(java.util.List.of(hospitalId)));
        model.addAttribute("requestDecisionColdAfterDays", requestDecisionArchives.getColdAfterDays());
        model.addAttribute("requestDecisionRetentionDays", requestDecisionArchives.getRetentionDays());
        model.addAttribute("quickDates", java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> LocalDate.now().minusDays(i)).toList());
        return "admin-audit";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit/view")
    public String viewAudit(@RequestParam LocalDate start, @RequestParam LocalDate end,
                            @RequestParam(required = false) Long medicineId,
                            HttpSession session, Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) return "redirect:/login";
        if (end.isBefore(start)) return "redirect:/admin/audit";
        model.addAttribute("title", medicineId == null ? "Audit spreadsheet" : "Resupply audit report");
        model.addAttribute("subtitle", medicineId == null
                ? "Read-only browser view of the selected audit period."
                : "Today's hospital log plus a dedicated 30-day history sheet for the selected medicine.");
        model.addAttribute("rows", auditArchiveService.reportRows(hospitalId, start, end, true));
        model.addAttribute("medicineRows", medicineId == null ? List.of()
                : auditArchiveService.reportRows(hospitalId, end.minusDays(29), end, true).stream()
                    .filter(r -> medicineId.equals(r.medicineId())).toList());
        model.addAttribute("medicineName", medicineId == null ? null : auditArchiveService.medicineDisplayName(medicineId));
        model.addAttribute("medicineId", medicineId);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("canDownload", true);
        model.addAttribute("adminView", true);
        model.addAttribute("quickDates", java.util.stream.IntStream.range(0, 30).mapToObj(i -> LocalDate.now().minusDays(i)).toList());
        return "audit-preview";
    }

    @GetMapping("/audit/export")
    public void exportAudit(@RequestParam LocalDate start, @RequestParam LocalDate end,
                             @RequestParam(required = false) Long medicineId,
                             HttpSession session, HttpServletResponse response) throws java.io.IOException {
        if (!"ADMIN".equals(session.getAttribute("role"))) { response.sendRedirect("/login"); return; }
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) { response.sendRedirect("/login"); return; }
        byte[] excel = auditArchiveService.exportExcel(hospitalId, start, end, true, medicineId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pulse-audit-" + start + "-to-" + end + ".xlsx\"");
        response.getOutputStream().write(excel);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/requests/audit/export")
    public void exportRequestDecisionAudit(@RequestParam LocalDate start, @RequestParam LocalDate end,
                                           HttpSession session, HttpServletResponse response) throws java.io.IOException {
        if (!"ADMIN".equals(session.getAttribute("role"))) { response.sendRedirect("/login"); return; }
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) { response.sendRedirect("/login"); return; }
        byte[] excel = requestDecisionArchives.exportExcelForHospital(hospitalId, start, end);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"pulse-request-decisions-" + start + "-to-" + end + ".xlsx\"");
        response.getOutputStream().write(excel);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit/medicine/{medicineId}")
    public String medicineAudit(@PathVariable Long medicineId, HttpSession session) {
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        return "redirect:/admin/audit/view?start=" + LocalDate.now() + "&end=" + LocalDate.now() + "&medicineId=" + medicineId;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/audit/seal")
    public String sealAuditDay(@RequestParam LocalDate day, HttpSession session, RedirectAttributes redirectAttributes) {
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) return "redirect:/login";
        try {
            redirectAttributes.addFlashAttribute("success", auditArchiveService.sealDayNow(hospitalId, day));
        } catch (RuntimeException ex) { redirectAttributes.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/audit";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/audit/requests/{id}/decision")
    public String decideAuditRequest(@PathVariable Long id, @RequestParam boolean approve,
                                     HttpSession session, RedirectAttributes redirectAttributes) {
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) return "redirect:/login";
        try {
            auditArchiveService.decideRequest(hospitalId, id, String.valueOf(session.getAttribute("username")), approve);
            redirectAttributes.addFlashAttribute("success", approve ? "Audit access approved." : "Audit access rejected.");
        } catch (RuntimeException ex) { redirectAttributes.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/audit";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        var hospital = dashboard.allHospitals().stream()
                .filter(h -> hospitalId != null && hospitalId.equals(h.getHospitalId()))
                .findFirst().orElse(null);
        if (hospital == null) return "redirect:/login";
        model.addAttribute("snapshot", dashboard.snapshot(java.util.List.of(hospital), HierarchyDashboardService.ScopeLevel.HOSPITAL));
        model.addAttribute("scopeTitle", hospital.getName());
        model.addAttribute("scopeSubtitle", "Hospital inventory, staff activity and local alerts");
        model.addAttribute("hospital", hospital);
        model.addAttribute("tierLabel", "HOSPITAL TIER");
        model.addAttribute("tierBadge", "VERIFIED · HOSPITAL");
        return "admin_dashboard";
    }
}
