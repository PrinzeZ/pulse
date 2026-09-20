package com.pulse.controller;

import com.pulse.SessionSecurity;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.StockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.service.AdminDashboardService;
import com.pulse.service.AlertService;
import com.pulse.service.StaffService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoleViewController {

    private final SessionSecurity sessionSecurity;
    private final AdminDashboardService adminDashboardService;
    private final StaffService staffService;
    private final AlertService alertService;
    private final StockEntryRepository stockEntryRepository;
    private final MedicineRepository medicineRepository;

    public RoleViewController(SessionSecurity sessionSecurity,
                              AdminDashboardService adminDashboardService,
                              StaffService staffService,
                              AlertService alertService,
                              StockEntryRepository stockEntryRepository,
                              MedicineRepository medicineRepository) {
        this.sessionSecurity = sessionSecurity;
        this.adminDashboardService = adminDashboardService;
        this.staffService = staffService;
        this.alertService = alertService;
        this.stockEntryRepository = stockEntryRepository;
        this.medicineRepository = medicineRepository;
    }



    @GetMapping("/admin/alerts")
    public String adminAlertsPage(Model model) {
        model.addAttribute("activeAlerts", alertService.getActiveAlerts());
        return "admin-alerts";
    }

    @GetMapping("/staff/dashboard")
    public String staffDashboard(HttpSession session, Model model) {
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
    public String staffStockPage(HttpSession session, Model model) {
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
            staffService.updateStock(staff.getHospitalId(), medicineId, quantity,
                    medicineRepository.findById(medicineId).orElse(null));
            redirectAttributes.addFlashAttribute("success", "Stock updated successfully.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Unable to update stock.");
        }
        return "redirect:/staff/stock";
    }
}
