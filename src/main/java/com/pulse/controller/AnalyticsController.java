package com.pulse.controller;

import com.pulse.model.Admin;
import com.pulse.model.DistrictAdmin;
import com.pulse.model.StateAdmin;
import com.pulse.SessionSecurity;
import com.pulse.service.AnalyticsService;
import com.pulse.service.HierarchyDashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final HierarchyDashboardService hierarchy;
    private final SessionSecurity security;

    public AnalyticsController(AnalyticsService analytics, HierarchyDashboardService hierarchy, SessionSecurity security) {
        this.analytics = analytics; this.hierarchy = hierarchy; this.security = security;
    }

    @GetMapping("/admin/analytics")
    public String hospital(HttpSession session, Model model) {
        Admin admin = security.getUser(session) instanceof Admin a ? a : null;
        if (admin == null || admin.getHospitalId() == null) return "redirect:/login";
        var hospitals = hierarchy.allHospitals().stream().filter(h -> admin.getHospitalId().equals(h.getHospitalId())).toList();
        model.addAttribute("tier", "HOSPITAL"); model.addAttribute("title", "Hospital analytics");
        model.addAttribute("scope", analytics.forScope(hospitals, HierarchyDashboardService.ScopeLevel.HOSPITAL));
        return "analytics";
    }

    @GetMapping("/district-admin/analytics")
    public String district(HttpSession session, Model model) {
        DistrictAdmin admin = security.getUser(session) instanceof DistrictAdmin a ? a : null;
        if (admin == null || admin.getDistrictId() == null) return "redirect:/login";
        String name = admin.getDistrictId() == 1L ? "Ernakulam" : admin.getDistrictId() == 2L ? "Kozhikode" : "District";
        var hospitals = hierarchy.hospitalsInDistrict(name);
        model.addAttribute("tier", "DISTRICT"); model.addAttribute("title", name + " analytics");
        model.addAttribute("scope", analytics.forScope(hospitals, HierarchyDashboardService.ScopeLevel.DISTRICT));
        return "analytics";
    }

    @GetMapping("/state-admin/analytics")
    public String state(HttpSession session, Model model) {
        StateAdmin admin = security.getUser(session) instanceof StateAdmin a ? a : null;
        if (admin == null || admin.getStateId() == null) return "redirect:/login";
        model.addAttribute("tier", "STATE"); model.addAttribute("title", "State analytics");
        model.addAttribute("scope", analytics.forScope(hierarchy.allHospitals(), HierarchyDashboardService.ScopeLevel.STATE));
        return "analytics";
    }
}
