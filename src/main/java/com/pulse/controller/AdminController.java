package com.pulse.controller;

import com.pulse.service.HierarchyDashboardService;
import com.pulse.service.StaffManagementService;
import com.pulse.service.StockLedgerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final HierarchyDashboardService dashboard;
    private final StaffManagementService staffManagement;
    private final StockLedgerService ledgerService;

    public AdminController(HierarchyDashboardService dashboard, StaffManagementService staffManagement, StockLedgerService ledgerService) {
        this.dashboard = dashboard;
        this.staffManagement = staffManagement;
        this.ledgerService = ledgerService;
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
    public String audit(HttpSession session, Model model) {
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) return "redirect:/login";
        model.addAttribute("hospitalId", hospitalId);
        model.addAttribute("movements", ledgerService.localMovements(hospitalId));
        model.addAttribute("verification", ledgerService.verifyLocal(hospitalId));
        return "admin-audit";
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
