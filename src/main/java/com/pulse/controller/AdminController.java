package com.pulse.controller;

import com.pulse.model.Alert;
import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AlertRepository alertRepository;
    private final StockEntryRepository stockEntryRepository;
    private final MedicineRepository medicineRepository;

    public AdminController(AlertRepository alertRepository,
                           StockEntryRepository stockEntryRepository,
                           MedicineRepository medicineRepository) {
        this.alertRepository = alertRepository;
        this.stockEntryRepository = stockEntryRepository;
        this.medicineRepository = medicineRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        String role = (String) session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return "redirect:/login";
        }

        // Active Low-Stock Alerts
        List<Alert> activeAlerts = alertRepository.findByResolvedFalseOrderByCreatedAtDesc();
        model.addAttribute("activeAlerts", activeAlerts);

        // Global Stock Inventory
        List<StockEntry> globalStock = stockEntryRepository.findAll();
        model.addAttribute("globalStock", globalStock);

        // Staff Activity / Audit Logs (assuming StockEntry has timestamp)
        List<StockEntry> recentUpdates = stockEntryRepository.findAll();
        recentUpdates.sort((a, b) -> b.getLastUpdated().compareTo(a.getLastUpdated()));
        model.addAttribute("recentUpdates", recentUpdates);

        // Approaching Expiry / Critical Shortages
        LocalDate today = LocalDate.now();
        List<Medicine> allMedicines = medicineRepository.findAll();
        List<Medicine> nearExpiry = allMedicines.stream()
                .filter(medicine -> medicine.getExpiryDate() != null && medicine.getExpiryDate().isBefore(today.plusMonths(3)))
                .toList();
        model.addAttribute("nearExpiry", nearExpiry);

        // Popular Searches / User Activity (placeholder - implement search logging)
        model.addAttribute("popularSearches", List.of("Medicine A", "Medicine B"));

        return "admin_dashboard";
    }
}