package com.pulse.controller;

import com.pulse.service.HierarchyDashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final HierarchyDashboardService dashboard;

    public AdminController(HierarchyDashboardService dashboard) {
        this.dashboard = dashboard;
    }

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
