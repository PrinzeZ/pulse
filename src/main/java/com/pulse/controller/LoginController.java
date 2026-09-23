package com.pulse.controller;

import com.pulse.SessionSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    private final SessionSecurity sessionSecurity;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public LoginController(SessionSecurity sessionSecurity) {
        this.sessionSecurity = sessionSecurity;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (sessionSecurity.hasRole(session, "STATE_ADMIN")) return "redirect:/state-admin/dashboard";
        if (sessionSecurity.hasRole(session, "DISTRICT_ADMIN")) return "redirect:/district-admin/dashboard";
        if (sessionSecurity.hasRole(session, "ADMIN")) return "redirect:/admin/dashboard";
        if (sessionSecurity.hasRole(session, "STAFF")) return "redirect:/staff/dashboard";
        return "index";
    }

    @GetMapping("/menu")
    public String menu(HttpSession session) {
        if (!sessionSecurity.isAuthenticated(session)) return "redirect:/login";
        if (sessionSecurity.hasRole(session, "STATE_ADMIN")) return "redirect:/state-admin/dashboard";
        if (sessionSecurity.hasRole(session, "DISTRICT_ADMIN")) return "redirect:/district-admin/dashboard";
        if (sessionSecurity.hasRole(session, "ADMIN")) return "redirect:/admin/dashboard";
        if (sessionSecurity.hasRole(session, "STAFF")) return "redirect:/staff/dashboard";
        return "redirect:/";
    }

    /** Browser-friendly GET logout retained for the existing P.U.L.S.E navigation. */
    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        return "redirect:/login?logout=true";
    }
}
