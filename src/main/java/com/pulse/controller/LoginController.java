package com.pulse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pulse.SessionSecurity;
import com.pulse.local.service.StockSyncService;
import com.pulse.model.Admin;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
import com.pulse.service.LoginService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {

    private final LoginService loginService;
    private final SessionSecurity sessionSecurity;
    private final StockSyncService stockSyncService;

    public LoginController(
            LoginService loginService,
            SessionSecurity sessionSecurity,
            StockSyncService stockSyncService) {

        this.loginService = loginService;
        this.sessionSecurity = sessionSecurity;
        this.stockSyncService = stockSyncService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (sessionSecurity.hasRole(session, "ADMIN")) {
            return "redirect:/admin/dashboard";
        }

        if (sessionSecurity.hasRole(session, "STAFF")) {
            return "redirect:/staff/dashboard";
        }

        if (sessionSecurity.isAuthenticated(session)) {
            session.invalidate();
        }

        return "index";
    }

    @PostMapping("/login")
    public String doLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        HttpSession newSession = request.getSession(true);

        try {
            User user = loginService.authenticate(username, password);

            newSession.setAttribute(
                    SessionSecurity.LOGGED_IN_USER,
                    user
            );

            if (user instanceof Admin) {
                newSession.setAttribute(
                        SessionSecurity.ROLE,
                        "ADMIN"
                );

                return "redirect:/admin/dashboard";
            }

            if (user instanceof PharmacyStaff) {
                newSession.setAttribute(
                        SessionSecurity.ROLE,
                        "STAFF"
                );

                PharmacyStaff staff = (PharmacyStaff) user;

                // Download this hospital's stock from PostgreSQL to H2
                stockSyncService.downloadHospitalStock(
                        staff.getHospitalId()
                );

                return "redirect:/staff/dashboard";
            }

        } catch (RuntimeException e) {
            return "redirect:/login?error=true";
        }

        return "redirect:/login?error=true";
    }

    @GetMapping("/menu")
    public String menu(HttpSession session) {

        if (!sessionSecurity.isAuthenticated(session)) {
            return "redirect:/login";
        }

        String role = (String) session.getAttribute(
                SessionSecurity.ROLE
        );

        if ("ADMIN".equals(role)) {
            return "redirect:/admin/dashboard";
        }

        if ("STAFF".equals(role)) {
            return "redirect:/staff/dashboard";
        }

        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout=true";
    }
}