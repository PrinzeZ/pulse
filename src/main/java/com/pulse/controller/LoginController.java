package com.pulse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pulse.model.Admin;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
import com.pulse.service.LoginService;

import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {

    @Autowired
    private LoginService loginService;

    @GetMapping("/login")
    public String loginPage(HttpSession session) {

        String role = (String) session.getAttribute("role");

        if ("ADMIN".equals(role)) {
            return "redirect:/admin/alerts";
        }

        if ("STAFF".equals(role)) {
            return "redirect:/staff/stock";
        }

        return "index";
    }

    @PostMapping("/login")
    public String doLogin(
            @RequestParam String username,
            @RequestParam String password,
            Model model,
            HttpSession session) {

        try {

            User user = loginService.authenticate(username, password);

            session.setAttribute("username", user.getUsername());

            if (user instanceof Admin) {
                session.setAttribute("role", "ADMIN");
                return "redirect:/admin/alerts";
            }

            if (user instanceof PharmacyStaff) {
                session.setAttribute("role", "STAFF");
                return "redirect:/staff/stock";
            }

        } catch (Exception e) {

            return "redirect:/login?error=true";
        }

        return "redirect:/login?error=true";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {

        session.invalidate();

        return "redirect:/login";
    }
}