package com.pulse.controller;

import com.pulse.service.LoginService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @Autowired
    private LoginService loginService;

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        String role = (String) session.getAttribute("role");
        if ("ADMIN".equals(role)) return "redirect:/admin/alerts";
        if ("STAFF".equals(role)) return "redirect:/staff/stock";
        return "index"; // maps to your login.html template
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username, @RequestParam String password, HttpSession session) {
        try {
            com.pulse.model.User user = loginService.authenticate(username, password);
            session.setAttribute("username", user.getUsername());

            if (user instanceof com.pulse.model.Admin) {
                session.setAttribute("role", "ADMIN");
                return "redirect:/admin/alerts";
            } else if (user instanceof com.pulse.model.PharmacyStaff) {
                session.setAttribute("role", "STAFF");
                session.setAttribute("hospitalId", ((com.pulse.model.PharmacyStaff) user).getHospitalId());
                return "redirect:/staff/stock";
            }
            return "redirect:/";
        } catch (Exception e) {
            return "redirect:/login?error=true";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}