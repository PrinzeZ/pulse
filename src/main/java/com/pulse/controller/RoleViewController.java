package com.pulse.controller;

import com.pulse.service.AlertService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoleViewController {

    private final AlertService alertService;

    public RoleViewController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/admin/alerts")
    public String adminAlertsPage(Model model) {
        model.addAttribute("activeAlerts", alertService.getActiveAlerts());
        return "admin-alerts";
    }
}