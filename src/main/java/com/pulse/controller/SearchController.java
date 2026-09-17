package com.pulse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SearchController {

    @GetMapping("/search")
    public String searchPage() {
        return "search"; // Maps to search.html inside templates
    }
}