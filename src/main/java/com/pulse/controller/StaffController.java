package com.pulse.controller;

import com.pulse.SessionSecurity;
import com.pulse.model.Medicine;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.StockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.service.StaffService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffController {

    private final SessionSecurity sessionSecurity;
    private final StaffService staffService;
    private final StockEntryRepository stockEntryRepository;
    private final MedicineRepository medicineRepository;

    public StaffController(SessionSecurity sessionSecurity,
                           StaffService staffService,
                           StockEntryRepository stockEntryRepository,
                           MedicineRepository medicineRepository) {
        this.sessionSecurity = sessionSecurity;
        this.staffService = staffService;
        this.stockEntryRepository = stockEntryRepository;
        this.medicineRepository = medicineRepository;
    }

    @GetMapping("/staff/dashboard")
    public String dashboard(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }
        StaffService.StaffInventory inventory = staffService.getInventory(staff.getHospitalId());
        if (inventory == null) {
            return "redirect:/login";
        }
        model.addAttribute("inventory", inventory);
        return "staff_dashboard";
    }

    @GetMapping("/staff/stock")
    public String stockPage(HttpSession session, Model model) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }
        StaffService.StaffInventory inventory = staffService.getInventory(staff.getHospitalId());
        if (inventory == null) {
            return "redirect:/login";
        }
        model.addAttribute("inventory", inventory);
        return "staff-stock";
    }

    @PostMapping("/staff/stock/update")
    public String updateStock(HttpSession session,
                              @RequestParam(required = false) Long entryId,
                              @RequestParam Long medicineId,
                              @RequestParam int quantity,
                              RedirectAttributes redirectAttributes) {
        PharmacyStaff staff = sessionSecurity.getStaff(session);
        if (staff == null || staff.getHospitalId() == null) {
            return "redirect:/login";
        }

        try {
            if (entryId != null) {
                StockEntry existing = stockEntryRepository.findById(entryId).orElse(null);
                if (existing == null || !staff.getHospitalId().equals(existing.getHospitalId())) {
                    return "redirect:/staff/stock?error=true";
                }
            }

            Medicine medicine = medicineRepository.findById(medicineId).orElse(null);
            staffService.updateStock(staff.getHospitalId(), medicineId, quantity, medicine);
            redirectAttributes.addFlashAttribute("success", "Stock updated successfully.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Unable to update stock.");
        }
        return "redirect:/staff/stock";
    }
}
