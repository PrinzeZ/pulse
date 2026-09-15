package com.pulse.controller;

import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.service.AlertService;
import com.pulse.service.StaffService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RoleViewController {

    @Autowired
    private AlertService alertService;

    @Autowired
    private StockEntryRepository stockEntryRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private StaffService staffService;

    @GetMapping("/admin/alerts")
    public String adminAlertsPage(HttpSession session, Model model) {
        String role = (String) session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return "redirect:/login"; // Security check
        }
        model.addAttribute("alerts", alertService.getActiveAlerts());
        return "admin-alerts";
    }

    @GetMapping("/staff/stock")
    public String staffStockPage(HttpSession session, Model model) {
        String role = (String) session.getAttribute("role");
        if (!"STAFF".equals(role) && !"ADMIN".equals(role)) {
            return "redirect:/login"; // Security check
        }

        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) {
            hospitalId = 1L; // fallback default
        }

        java.util.Map<Long, com.pulse.model.StockEntry> stockMap = stockEntryRepository.findByHospitalId(hospitalId)
                .stream().collect(java.util.stream.Collectors.toMap(com.pulse.model.StockEntry::getMedId, s -> s, (s1, s2) -> s1));

        java.util.List<java.util.Map<String, Object>> inventory = new java.util.ArrayList<>();
        for (com.pulse.model.Medicine med : medicineRepository.findAll()) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("medicineId", med.getMedId());
            row.put("name", med.getName());
            row.put("category", med.getCat());
            row.put("threshold", med.getThreshold());
            com.pulse.model.StockEntry entry = stockMap.get(med.getMedId());
            int qty = entry != null ? entry.getQuantity() : 0;
            row.put("quantity", qty);
            row.put("isLow", qty < med.getThreshold());
            inventory.add(row);
        }

        model.addAttribute("inventory", inventory);
        return "staff-stock";
    }

    @PostMapping("/staff/stock/update")
    public String updateStock(@RequestParam Long medicineId,
                              @RequestParam int quantity,
                              HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"STAFF".equals(role) && !"ADMIN".equals(role)) {
            return "redirect:/login";
        }

        Long hospitalId = (Long) session.getAttribute("hospitalId");
        if (hospitalId == null) {
            hospitalId = 1L;
        }

        staffService.updateStock(hospitalId, medicineId, quantity);
        return "redirect:/staff/stock?success=true";
    }
}