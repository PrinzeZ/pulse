package com.pulse.controller;

import com.pulse.SessionSecurity;
import com.pulse.model.Admin;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.StateAdmin;
import com.pulse.model.DistrictAdmin;
import com.pulse.model.User;
import com.pulse.service.LoginService;
import com.pulse.local.service.StockSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final LoginService loginService;
    private final SessionSecurity sessionSecurity;
    private final ObjectProvider<StockSyncService> stockSyncServiceProvider;

    public LoginController(
            LoginService loginService,
            SessionSecurity sessionSecurity,
            ObjectProvider<StockSyncService> stockSyncServiceProvider) {
        this.loginService = loginService;
        this.sessionSecurity = sessionSecurity;
        this.stockSyncServiceProvider = stockSyncServiceProvider;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        // Check new roles first
        if (sessionSecurity.hasRole(session, "STATE_ADMIN")) {
            return "redirect:/state-admin/dashboard";
        }
        if (sessionSecurity.hasRole(session, "DISTRICT_ADMIN")) {
            return "redirect:/district-admin/dashboard";
        }
        // Existing role checks
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
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        try {
            User user = loginService.authenticate(username, password);
            newSession.setAttribute(SessionSecurity.LOGGED_IN_USER, user);
            newSession.setAttribute("stateId", user.getStateId());
            newSession.setAttribute("districtId", user.getDistrictId());
            newSession.setAttribute("hospitalId", user.getHospitalId());
            String role = determineRole(user);
            newSession.setAttribute(SessionSecurity.ROLE, role);

            // When the optional "local" profile is active, refresh this hospital's
            // local H2 stock cache after staff login. Normal PostgreSQL mode is unchanged.
            if ("STAFF".equals(role) && user.getHospitalId() != null) {
                StockSyncService syncService = stockSyncServiceProvider.getIfAvailable();
                if (syncService != null) {
                    syncService.syncHospital(user.getHospitalId());
                }
            }

            return redirectToDashboard(role);
        } catch (RuntimeException e) {
            return "redirect:/login?error=true";
        }
    }

    private String determineRole(User user) {
        if (user instanceof StateAdmin) {
            return "STATE_ADMIN";
        }
        if (user instanceof DistrictAdmin) {
            return "DISTRICT_ADMIN";
        }
        if (user instanceof Admin) {
            return "ADMIN";
        }
        if (user instanceof PharmacyStaff) {
            return "STAFF";
        }
        return "";
    }

    private String redirectToDashboard(String role) {
        switch (role) {
            case "STATE_ADMIN":
                return "redirect:/state-admin/dashboard";
            case "DISTRICT_ADMIN":
                return "redirect:/district-admin/dashboard";
            case "ADMIN":
                return "redirect:/admin/dashboard";
            case "STAFF":
                return "redirect:/staff/dashboard";
            default:
                return "redirect:/login?error=true";
        }
    }

    @GetMapping("/menu")
    public String menu(HttpSession session) {
        if (!sessionSecurity.isAuthenticated(session)) {
            return "redirect:/login";
        }
        String role = (String) session.getAttribute(SessionSecurity.ROLE);
        switch (role) {
            case "STATE_ADMIN":
                return "redirect:/state-admin/dashboard";
            case "DISTRICT_ADMIN":
                return "redirect:/district-admin/dashboard";
            case "ADMIN":
                return "redirect:/admin/dashboard";
            case "STAFF":
                return "redirect:/staff/dashboard";
            default:
                return "redirect:/";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout=true";
    }
}
