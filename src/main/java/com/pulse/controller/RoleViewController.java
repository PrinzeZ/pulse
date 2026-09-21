package com.pulse.controller;

import com.pulse.service.AlertService;
import com.pulse.SessionSecurity;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoleViewController {

    private final AlertService alertService;
    private final SessionSecurity sessionSecurity;

    public RoleViewController(AlertService alertService, SessionSecurity sessionSecurity) {
        this.alertService = alertService;
        this.sessionSecurity = sessionSecurity;
    }

    @GetMapping("/admin/alerts")
    public String adminAlertsPage(HttpSession session, Model model) {
        if (!sessionSecurity.isAuthenticated(session)) return "redirect:/login";
        var alerts = alertService.getActiveAlerts();
        if (sessionSecurity.hasRole(session, "ADMIN")) {
            Long hospitalId = (Long) session.getAttribute("hospitalId");
            alerts = alerts.stream().filter(a -> hospitalId != null && hospitalId.equals(a.getHospitalId())).toList();
        }
        model.addAttribute("activeAlerts", alerts);
        return "admin-alerts";
    }
}