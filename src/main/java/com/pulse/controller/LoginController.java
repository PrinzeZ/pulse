package com.pulse.controller;

import com.pulse.model.Admin;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
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
        if ("ADMIN".equals(role)) return "redirect:/admin";
        if ("STAFF".equals(role)) return "redirect:/staff";
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username, @RequestParam String password, Model model, HttpSession session) {
        try {
            User user = loginService.authenticate(username, password);

            String role = "STAFF";
            if (user instanceof Admin) {
                role = "ADMIN";
            }

            session.setAttribute("username", username);
            session.setAttribute("role", role);

            if ("ADMIN".equals(role)) return "redirect:/admin";
            return "redirect:/staff";

        } catch (Exception e) {
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}