package com.pulse.controller;

import com.pulse.model.HospitalRegistration;
import com.pulse.service.GovernmentHospitalCatalogService;
import com.pulse.service.HospitalProvisioningService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/hospital")
public class HospitalRegistrationController {

    private final HospitalProvisioningService provisioning;
    private final GovernmentHospitalCatalogService catalog;

    public HospitalRegistrationController(HospitalProvisioningService provisioning,
                                           GovernmentHospitalCatalogService catalog) {
        this.provisioning = provisioning;
        this.catalog = catalog;
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("governmentHospitals", hospitalMapPayload());
        return "hospital/register";
    }

    private java.util.List<java.util.Map<String, Object>> hospitalMapPayload() {
        return catalog.all().stream().map(h -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("key", h.key()); row.put("name", h.name()); row.put("district", h.district());
            row.put("category", h.category()); row.put("latitude", h.latitude()); row.put("longitude", h.longitude());
            row.put("address", h.address()); row.put("closed", h.closed()); row.put("googleMapsUrl", h.googleMapsUrl());
            return row;
        }).toList();
    }

    @GetMapping("/districts")
    @ResponseBody
    public Object districts(@RequestParam Long stateId) {
        return provisioning.districts(stateId);
    }

    @GetMapping("/government-hospitals")
    @ResponseBody
    public Object governmentHospitals(@RequestParam(required = false, defaultValue = "") String district) {
        return catalog.byDistrict(district).stream().map(h -> java.util.Map.of(
                "key", h.key(), "name", h.name(), "district", h.district(), "category", h.category(),
                "latitude", h.latitude(), "longitude", h.longitude(), "address", h.address(), "closed", h.closed(),
                "googleMapsUrl", h.googleMapsUrl())).toList();
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String governmentHospitalKey,
            @RequestParam String hospitalEmail,
            @RequestParam String adminName,
            @RequestParam String adminUsername,
            Model model) {
        try {
            HospitalRegistration registration = provisioning.register(
                    governmentHospitalKey, hospitalEmail, adminName, adminUsername);
            model.addAttribute("registration", registration);
            return "hospital/registration-success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("governmentHospitals", hospitalMapPayload());
            return "hospital/register";
        }
    }

    @GetMapping("/verify")
    public String verify(@RequestParam String token, Model model) {
        try {
            provisioning.verifyEmail(token);
            model.addAttribute("success", true);
        } catch (IllegalArgumentException e) {
            model.addAttribute("success", false);
            model.addAttribute("error", e.getMessage());
        }
        return "hospital/verify";
    }

    @PostMapping("/verify-code")
    public String verifyCode(@RequestParam String email, @RequestParam String code, Model model) {
        try {
            provisioning.verifyEmailCode(email, code);
            model.addAttribute("success", true);
        } catch (IllegalArgumentException e) {
            model.addAttribute("success", false);
            model.addAttribute("error", e.getMessage());
        }
        return "hospital/verify";
    }

    @GetMapping("/setup")
    public String setupPage(@RequestParam String token, Model model) {
        try {
            provisioning.activation(token);
            model.addAttribute("token", token);
            return "hospital/setup";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "hospital/setup";
        }
    }

    @PostMapping("/setup")
    public String setup(@RequestParam String token, @RequestParam String password,
                        @RequestParam String confirmation, Model model) {
        try {
            provisioning.setInitialPassword(token, password, confirmation);
            return "redirect:/login?activated=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("token", token);
            model.addAttribute("error", e.getMessage());
            return "hospital/setup";
        }
    }
}
