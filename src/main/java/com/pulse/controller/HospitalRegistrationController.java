package com.pulse.controller;

import com.pulse.model.HospitalRegistration;
import com.pulse.service.HospitalProvisioningService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/hospital")
public class HospitalRegistrationController {

    private final HospitalProvisioningService provisioning;

    public HospitalRegistrationController(HospitalProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("states", provisioning.states());
        return "hospital/register";
    }

    @GetMapping("/districts")
    @ResponseBody
    public Object districts(@RequestParam Long stateId) {
        return provisioning.districts(stateId);
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String hospitalName,
            @RequestParam String hospitalEmail,
            @RequestParam String adminName,
            @RequestParam String adminUsername,
            @RequestParam Long stateId,
            @RequestParam Long districtId,
            Model model) {
        try {
            HospitalRegistration registration = provisioning.register(
                    hospitalName, hospitalEmail, adminName, adminUsername, stateId, districtId);
            model.addAttribute("registration", registration);
            return "hospital/registration-success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("states", provisioning.states());
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
    public String verifyCode(
            @RequestParam String email,
            @RequestParam String code,
            Model model) {
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
    public String setup(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmation,
            Model model) {
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
