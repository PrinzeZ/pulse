package com.pulse.controller;

import com.pulse.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController
 {

    @Autowired
    private SearchService searchService;

    @GetMapping("/user")
    public String userPage(Model model) 
    {
        return "user";
    }

    @PostMapping("/user")
    public String search(@RequestParam String medicine,@RequestParam String district, Model model) 
{
 model.addAttribute("results",searchService.search(district, medicine));
        return "user";
    }
}
