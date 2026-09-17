package com.pulse.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;

import jakarta.servlet.http.HttpSession;

@Controller
public class RoleViewController {

    private final StockEntryRepository stockEntryRepository;
    private final MedicineRepository medicineRepository;

    public RoleViewController(StockEntryRepository stockEntryRepository,
                              MedicineRepository medicineRepository) {

        this.stockEntryRepository = stockEntryRepository;
        this.medicineRepository = medicineRepository;
    }

    @GetMapping("/admin/alerts")
    public String adminAlertsPage(HttpSession session) {

        String role = (String) session.getAttribute("role");

        if (!"ADMIN".equals(role)) {
            return "redirect:/login";
        }

        return "admin-alerts";
    }


    @GetMapping("/staff/stock")
    public String staffStockPage(HttpSession session, Model model) {

        String role = (String) session.getAttribute("role");

        if (!"STAFF".equals(role) && !"ADMIN".equals(role)) {
            return "redirect:/login";
        }

        String username = (String) session.getAttribute("username");

        Long hospitalId = null;

        if ("staff_kozhikode".equals(username)) {
            hospitalId = 1L;

        } else if ("staff_ernakulam".equals(username)) {
            hospitalId = 3L;
        }

        if (hospitalId == null) {
            return "redirect:/login";
        }


        // Get ALL 10 medicines
        List<Medicine> allMedicines = medicineRepository.findAll();


        // Get existing stock entries for this hospital
        List<StockEntry> existingStock =
                stockEntryRepository.findByHospitalId(hospitalId);


        // Put existing stock into a map using medicine ID
        Map<Long, StockEntry> stockMap = existingStock.stream()
                .collect(Collectors.toMap(
                        StockEntry::getMedId,
                        stock -> stock
                ));


        // Create a stock entry for every medicine
        List<StockEntry> stockEntries = new ArrayList<>();

        for (Medicine medicine : allMedicines) {

            StockEntry stock = stockMap.get(medicine.getMedId());

            if (stock == null) {

                stock = new StockEntry();

                stock.setHospitalId(hospitalId);
                stock.setMedId(medicine.getMedId());
                stock.setQuantity(0);

            }

            stockEntries.add(stock);
        }


        // Send data to Thymeleaf
        Map<Long, Medicine> medicines = allMedicines.stream()
                .collect(Collectors.toMap(
                        Medicine::getMedId,
                        medicine -> medicine
                ));

        model.addAttribute("stockEntries", stockEntries);
        model.addAttribute("medicines", medicines);

        return "staff-stock";
    }
}