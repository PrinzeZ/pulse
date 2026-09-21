package com.pulse.controller;

import com.pulse.service.HospitalProvisioningService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OperationsController {
    private final HospitalProvisioningService provisioning;

    public OperationsController(HospitalProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @GetMapping("/admin/requests")
    public String hospitalRequests(HttpSession session, Model model) {
        if (!"ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        model.addAttribute("tier", "HOSPITAL");
        model.addAttribute("pageTitle", "Hospital requests");
        model.addAttribute("pageSubtitle", "Requests submitted by this hospital will appear here.");
        return "operations/requests";
    }

    @GetMapping("/district-admin/requests")
    public String districtRequests(HttpSession session, Model model) {
        if (!"DISTRICT_ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        model.addAttribute("tier", "DISTRICT");
        model.addAttribute("pageTitle", "District requests");
        model.addAttribute("pageSubtitle", "Only submitted hospital requests for your district will appear here.");
        return "operations/requests";
    }

    @GetMapping("/state-admin/requests")
    public String stateRequests(HttpSession session, Model model) {
        if (!"STATE_ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        model.addAttribute("tier", "STATE");
        model.addAttribute("pageTitle", "State requests");
        model.addAttribute("pageSubtitle", "Only requests escalated by district administrators will appear here.");
        return "operations/requests";
    }

    @GetMapping("/district-admin/verifications")
    public String districtVerifications(HttpSession session, Model model) {
        if (!"DISTRICT_ADMIN".equals(session.getAttribute("role"))) return "redirect:/login";
        Long districtId = (Long) session.getAttribute("districtId");
        if (districtId == null) return "redirect:/login";

        model.addAttribute("registrations", provisioning.registrationsForDistrict(districtId));
        return "district-admin/verifications";
    }
}
