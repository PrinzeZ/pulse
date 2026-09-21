package com.pulse.controller;

import com.pulse.service.HospitalProvisioningService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/district-admin/hospital-registrations")
public class DistrictHospitalRegistrationController {

    private final HospitalProvisioningService provisioning;

    public DistrictHospitalRegistrationController(HospitalProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        if (!"DISTRICT_ADMIN".equals(session.getAttribute("role"))) {
            return "redirect:/login";
        }

        Long districtId = (Long) session.getAttribute("districtId");
        if (districtId == null) {
            return "redirect:/login";
        }

        model.addAttribute("registrations",
                provisioning.verifiedRegistrationsForDistrict(districtId));
        return "district-admin/hospital-registrations";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, HttpSession session, Model model) {
        if (!"DISTRICT_ADMIN".equals(session.getAttribute("role"))) {
            return "redirect:/login";
        }

        Long districtId = (Long) session.getAttribute("districtId");
        if (districtId == null) {
            return "redirect:/login";
        }

        try {
            String activationToken = provisioning.approve(id, districtId);
            model.addAttribute("activationToken", activationToken);
            return "district-admin/activation-success";
        } catch (IllegalArgumentException e) {
            return "redirect:/district-admin/hospital-registrations?error=true";
        }
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, HttpSession session) {
        if (!"DISTRICT_ADMIN".equals(session.getAttribute("role"))) {
            return "redirect:/login";
        }

        Long districtId = (Long) session.getAttribute("districtId");
        if (districtId == null) {
            return "redirect:/login";
        }

        try {
            provisioning.reject(id, districtId);
        } catch (IllegalArgumentException ignored) {
            // Keep registration details out of the response.
        }

        return "redirect:/district-admin/hospital-registrations";
    }
}
