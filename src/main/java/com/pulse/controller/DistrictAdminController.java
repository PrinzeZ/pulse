package com.pulse.controller;

import com.pulse.service.HierarchyDashboardService;
import com.pulse.service.HospitalProvisioningService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DistrictAdminController {

    private final HierarchyDashboardService dashboard;
    private final HospitalProvisioningService provisioning;

    public DistrictAdminController(HierarchyDashboardService dashboard, HospitalProvisioningService provisioning) {
        this.dashboard = dashboard;
        this.provisioning = provisioning;
    }

    @GetMapping("/district-admin/dashboard")
    public String dashboard(HttpSession session, Model model) {

        if (!"DISTRICT_ADMIN".equals(session.getAttribute("role"))) {
            return "redirect:/login";
        }

        Long districtId = (Long) session.getAttribute("districtId");

        if (districtId == null) {
            return "redirect:/login";
        }

        String district = districtName(districtId);

        if ("Unknown".equals(district)) {
            return "redirect:/login";
        }

        var hospitals = dashboard.hospitalsInDistrict(district);

        model.addAttribute("snapshot", dashboard.snapshot(hospitals, HierarchyDashboardService.ScopeLevel.DISTRICT));
        model.addAttribute("scopeTitle", district + " District");
        model.addAttribute(
                "scopeSubtitle",
                "District hospital inventory, shortages and activity"
        );
        model.addAttribute("tierLabel", "DISTRICT TIER");
        model.addAttribute("tierBadge", "VERIFIED · DISTRICT");

        return "district-admin/dashboard";
    }

    @GetMapping("/district-admin/verifications")
    public String verifications(HttpSession session, Model model) {
        if (!"DISTRICT_ADMIN".equals(session.getAttribute("role"))) {
            return "redirect:/login";
        }
        Long districtId = (Long) session.getAttribute("districtId");
        if (districtId == null) {
            return "redirect:/login";
        }
        model.addAttribute("registrations", provisioning.verifiedRegistrationsForDistrict(districtId));
        model.addAttribute("tierBadge", "VERIFIED · DISTRICT");
        return "district-admin/verifications";
    }

    private String districtName(Long districtId) {
        if (districtId == 1L) {
            return "Ernakulam";
        }

        if (districtId == 2L) {
            return "Kozhikode";
        }

        return "Unknown";
    }
}