package com.pulse.controller;

import com.pulse.service.HierarchyDashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StateAdminController {
    private final HierarchyDashboardService dashboard;

    public StateAdminController(HierarchyDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/state-admin/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!"STATE_ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        var hospitals = dashboard.allHospitals();
        var snapshot = dashboard.snapshot(hospitals, HierarchyDashboardService.ScopeLevel.STATE);
        model.addAttribute("snapshot", snapshot);
        model.addAttribute("scopeTitle", "Kerala State");
        model.addAttribute("scopeSubtitle", "Statewide medicine inventory and hospital operations");
        model.addAttribute("tierLabel", "STATE TIER");
        model.addAttribute("tierBadge", "VERIFIED · STATE");
        return "state-admin/dashboard";
    }
}
