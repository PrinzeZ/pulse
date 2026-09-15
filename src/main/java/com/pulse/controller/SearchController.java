package com.pulse.controller;

import com.pulse.dto.SearchResult;
import com.pulse.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class SearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/search")
    public String searchPage(@RequestParam(required = false) String query,
                             @RequestParam(required = false) String district,
                             Model model) {
        if (query != null && !query.isBlank()) {
            List<SearchResult> results = searchService.search(district, query);
            model.addAttribute("results", results);
            model.addAttribute("query", query);
            model.addAttribute("district", district);
        }
        return "search"; // Maps to search.html inside templates
    }
}