package com.pulse.controller;

import com.pulse.service.GovernmentHospitalCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MapController {
    private final GovernmentHospitalCatalogService catalog;

    public MapController(GovernmentHospitalCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/map")
    public String mapPage(Model model) {
        model.addAttribute("mapHospitalCount", catalog.all().size());
        return "map";
    }
}
