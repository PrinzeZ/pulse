package com.pulse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SecurityErrorController {
    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
