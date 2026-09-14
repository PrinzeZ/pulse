package com.pulse.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoleViewController {

    @GetMapping("/admin/alerts")
    public String adminAlertsPage(HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return "redirect:/login"; // Security check
        }
        return "admin-alerts";
    }

    @GetMapping("/staff/stock")
    public String staffStockPage(HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"STAFF".equals(role) && !"ADMIN".equals(role)) {
            return "redirect:/login"; // Security check
        }
        return "staff-stock";
    }
}