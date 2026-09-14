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
    public String doLogin(@RequestParam String username, @RequestParam String password, Model model, HttpSession session) {
        if ("admin_main".equals(username) && "admin123".equals(password)) {
            session.setAttribute("username", username);
            session.setAttribute("role", "ADMIN");
            return "redirect:/admin/alerts";
        } else if ("staff_kozhikode".equals(username) && "admin123".equals(password)) {
            session.setAttribute("username", username);
            session.setAttribute("role", "STAFF");
            return "redirect:/staff/stock";
        }

        return "redirect:/login?error=true";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}