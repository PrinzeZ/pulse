package com.pulse.controller;

import com.pulse.SessionSecurity;
import com.pulse.model.Admin;
import com.pulse.model.DistrictAdmin;
import com.pulse.model.StateAdmin;
import com.pulse.service.MedicineRequestService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OperationsController {

    private final MedicineRequestService requests;
    private final SessionSecurity security;

    public OperationsController(MedicineRequestService requests, SessionSecurity security) {
        this.requests = requests;
        this.security = security;
    }

    @GetMapping("/admin/requests")
    public String hospitalRequests(HttpSession session, Model model) {
        Admin admin = security.getUser(session) instanceof Admin a ? a : null;
        if (admin == null || admin.getHospitalId() == null) return "redirect:/login";

        model.addAttribute("tier", "HOSPITAL");
        model.addAttribute("pageTitle", "Medicine requests");
        model.addAttribute("pageSubtitle", "Create and track medicine requests for your hospital.");
        model.addAttribute("requests", requests.hospitalViews(admin.getHospitalId()));
        model.addAttribute("medicines", requests.availableMedicines());
        return "operations/requests";
    }

    @PostMapping("/admin/requests/sync")
    public String syncHospitalRequests(HttpSession session, RedirectAttributes redirectAttributes) {
        Admin admin = security.getUser(session) instanceof Admin a ? a : null;
        if (admin == null || admin.getHospitalId() == null) return "redirect:/login";
        var result = requests.syncHospital(admin.getHospitalId());
        if (result.status().startsWith("OFFLINE:")) {
            redirectAttributes.addFlashAttribute("error",
                    "Cloud unavailable. Local requests are safe and remain queued.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Request sync complete. Pushed " + result.pushed() + " and refreshed " + result.pulled() + " request(s).");
        }
        return "redirect:/admin/requests";
    }

    @PostMapping("/admin/requests/create")
    public String createHospitalRequest(HttpSession session,
                                        @RequestParam Long medicineId,
                                        @RequestParam int quantity,
                                        @RequestParam(required = false) String note,
                                        RedirectAttributes redirectAttributes) {
        Admin admin = security.getUser(session) instanceof Admin a ? a : null;
        if (admin == null || admin.getHospitalId() == null) return "redirect:/login";

        try {
            var result = requests.createHospitalRequest(
                    admin.getHospitalId(),
                    admin.getDistrictId(),
                    admin.getStateId(),
                    medicineId,
                    quantity,
                    note,
                    admin.getUsername());
            redirectAttributes.addFlashAttribute("success", result.message());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "The medicine request could not be saved.");
        }
        return "redirect:/admin/requests";
    }

    @GetMapping("/district-admin/requests")
    public String districtRequests(HttpSession session, Model model) {
        DistrictAdmin admin = security.getUser(session) instanceof DistrictAdmin a ? a : null;
        if (admin == null || admin.getDistrictId() == null) return "redirect:/login";

        model.addAttribute("tier", "DISTRICT");
        model.addAttribute("pageTitle", "District requests");
        model.addAttribute("pageSubtitle", "Review hospital requests within your district.");
        model.addAttribute("requests", requests.districtViews(admin.getDistrictId()));
        return "operations/requests";
    }

    @PostMapping("/district-admin/requests/{id}/action")
    public String districtAction(@PathVariable Long id,
                                 @RequestParam String action,
                                 @RequestParam(required = false) Integer fulfilledQuantity,
                                 @RequestParam(required = false) String note,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        DistrictAdmin admin = security.getUser(session) instanceof DistrictAdmin a ? a : null;
        if (admin == null || admin.getDistrictId() == null) return "redirect:/login";

        try {
            requests.districtAction(admin.getDistrictId(), id, action, fulfilledQuantity, note, admin.getUsername());
            redirectAttributes.addFlashAttribute("success", "Request updated successfully.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "The request action could not be completed.");
        }
        return "redirect:/district-admin/requests";
    }

    @GetMapping("/state-admin/requests")
    public String stateRequests(HttpSession session, Model model) {
        StateAdmin admin = security.getUser(session) instanceof StateAdmin a ? a : null;
        if (admin == null || admin.getStateId() == null) return "redirect:/login";

        model.addAttribute("tier", "STATE");
        model.addAttribute("pageTitle", "State requests");
        model.addAttribute("pageSubtitle", "Review requests escalated by district administrators.");
        model.addAttribute("requests", requests.stateViews(admin.getStateId()));
        return "operations/requests";
    }

    @PostMapping("/state-admin/requests/{id}/action")
    public String stateAction(@PathVariable Long id,
                              @RequestParam String action,
                              @RequestParam(required = false) Integer fulfilledQuantity,
                              @RequestParam(required = false) String note,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        StateAdmin admin = security.getUser(session) instanceof StateAdmin a ? a : null;
        if (admin == null || admin.getStateId() == null) return "redirect:/login";

        try {
            requests.stateAction(admin.getStateId(), id, action, fulfilledQuantity, note, admin.getUsername());
            redirectAttributes.addFlashAttribute("success", "State request action saved.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "The state request action could not be completed.");
        }
        return "redirect:/state-admin/requests";
    }
}
