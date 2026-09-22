package com.pulse.controller;

import com.pulse.dto.SearchResult;
import com.pulse.model.StockStatus;
import com.pulse.model.Medicine;
import com.pulse.model.StockEntry;
import com.pulse.local.model.LocalStockEntry;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.service.SearchService;
import com.pulse.local.service.LocalOfflineStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Controller
public class SearchController {

    private final SearchService searchService;
    private final HospitalRepository hospitalRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final MedicineRepository medicineRepository;
    private final StockEntryRepository stockEntryRepository;
    private final ObjectProvider<com.pulse.local.repository.LocalStockEntryRepository> localStockProvider;

    public SearchController(SearchService searchService, HospitalRepository hospitalRepository,
                             ObjectProvider<LocalOfflineStore> localStoreProvider,
                             MedicineRepository medicineRepository,
                             StockEntryRepository stockEntryRepository,
                             ObjectProvider<com.pulse.local.repository.LocalStockEntryRepository> localStockProvider) {
        this.searchService = searchService;
        this.hospitalRepository = hospitalRepository;
        this.localStoreProvider = localStoreProvider;
        this.medicineRepository = medicineRepository;
        this.stockEntryRepository = stockEntryRepository;
        this.localStockProvider = localStockProvider;
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "query", required = false, defaultValue = "") String query,
                         @RequestParam(name = "district", required = false, defaultValue = "") String district,
                         Model model) {
        List<SearchResult> results = searchService.search(district, query);
        model.addAttribute("results", results);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("selectedDistrict", district == null ? "" : district);
        model.addAttribute("district", district == null ? "" : district);
        model.addAttribute("districts", districts());
        model.addAttribute("districtStatuses", districtStatuses(results));
        model.addAttribute("medicineCards", medicineCards(results));
        model.addAttribute("searched", (query != null && !query.isBlank()) || (district != null && !district.isBlank()));
        return "search";
    }


    @GetMapping("/medicine/{id}")
    public String medicineDetails(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
        try {
            Medicine medicine = medicineRepository.findById(id).orElseThrow();
            List<MedicineAvailability> availability = new ArrayList<>();
            for (StockEntry entry : stockEntryRepository.findAll()) {
                if (!id.equals(entry.getMedId())) continue;
                hospitalRepository.findById(entry.getHospitalId()).ifPresent(hospital ->
                        availability.add(new MedicineAvailability(
                                hospital.getName(), hospital.getDistrict(), entry.getQuantity(),
                                medicine.getThreshold(), entry.getLastUpdated(),
                                StockStatus.from(entry.getQuantity(), medicine.getThreshold()).name())));
            }
            model.addAttribute("medicine", medicine);
            model.addAttribute("medicineImage", medicineImage(medicine.getName()));
            model.addAttribute("localMedicineImage", localMedicineImage(medicine.getName()));
            model.addAttribute("availability", availability);
            return "medicine-details";
        } catch (RuntimeException cloudFailure) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            if (local == null) throw cloudFailure;
            Medicine medicine = local.findMedicine(id).orElseThrow(() -> cloudFailure);
            List<MedicineAvailability> availability = new ArrayList<>();
            var stockRepo = localStockProvider.getIfAvailable();
            if (stockRepo != null) {
                for (LocalStockEntry entry : stockRepo.findByMedicineId(id)) {
                    local.findHospital(entry.getHospitalId()).ifPresent(hospital ->
                            availability.add(new MedicineAvailability(
                                    hospital.getName(), hospital.getDistrict(), entry.getQuantity(),
                                    medicine.getThreshold(), null,
                                    StockStatus.from(entry.getQuantity(), medicine.getThreshold()).name())));
                }
            }
            model.addAttribute("medicine", medicine);
            model.addAttribute("medicineImage", medicineImage(medicine.getName()));
            model.addAttribute("localMedicineImage", localMedicineImage(medicine.getName()));
            model.addAttribute("availability", availability);
            return "medicine-details";
        }
    }

    public static String medicineImage(String name) {
        if (name == null) return "/images/medicines/paracetamol.png";
        return switch (name.trim().toLowerCase()) {
            case "paracetamol" -> "https://www.pharmacydirect.co.nz/images/T/Paracare-Tablets-100---Quantity-Restriction--1--Applies-Expiry---04-2020-PARAcare-Paracetamol-500mg-Tabs-100.png";
            case "insulin" -> "https://topthuoc.vn/web/image/product.template/6153/image_1024?unique=34cd610";
            case "amoxicillin" -> "https://cevpharma.com.vn/web/image/product.template/6359/image_1024?unique=29fedd0";
            case "metformin" -> "https://www.emedbucket.com/emedpro_img/uploads/product/main/healthglowpharmacy/1633679441_3011_0.jpg";
            case "amlodipine" -> "https://medecify.com/storages/2024/10/DF-EEDF-A-BE-F-B-1-3000x2769.jpeg";
            case "omeprazole" -> "https://5.imimg.com/data5/SELLER/Default/2024/6/426792709/GE/IR/UX/133816967/omez-20-mg-500x500.jpg";
            case "cetirizine" -> "https://frankrosspharmacy.com/_next/image?q=75&url=https%3A%2F%2Femami-production-2.s3.amazonaws.com%2Fvariant_images%2Ffiles%2F000%2F030%2F892%2Fnormal_webp%2FFR-39958.webp%3F1659683123&w=640";
            case "azithromycin" -> "https://rigmeds-main.s3.ap-south-1.amazonaws.com/53528/Azibact-500-5tab-1.jpg";
            case "salbutamol" -> "https://thecarepharmacy.com/wp-content/uploads/2025/04/Cellbutamol-Inhaler-The-Care-Pharmacy.jpg";
            case "ors sachets" -> "https://asset.sastasundar.com/incom/images/product/ORS-Orange-Flavour-Sachet-1771928186-10162673-a.jpg";
            case "ibuprofen" -> "https://shop.rowlandspharmacy.co.uk/cdn/shop/files/NumarkIbuprofen200mgx16_3D_Visual.png?v=1744196378&width=1024";
            default -> "/images/medicines/" + name.trim().toLowerCase().replace(" ", "-") + ".png";
        };
    }

    public static String localMedicineImage(String name) {
        if (name == null) return "/images/medicines/paracetamol.png";
        return "/images/medicines/" + name.trim().toLowerCase().replace(" ", "-") + ".png";
    }

    public static class MedicineCard {
        private final Long medicineId;
        private final String medicineName;
        private final String category;
        private final String imageUrl;
        private final String fallbackImageUrl;
        private int hospitalCount;
        private int availableHospitalCount;
        private String overallStatus = "RED";

        public MedicineCard(Long medicineId, String medicineName, String category) {
            this.medicineId = medicineId;
            this.medicineName = medicineName;
            this.category = category;
            this.imageUrl = medicineImage(medicineName);
            this.fallbackImageUrl = localMedicineImage(medicineName);
        }
        public void add(SearchResult result) {
            hospitalCount++;
            if ("GREEN".equals(result.getStatus())) availableHospitalCount++;
            if ("GREEN".equals(result.getStatus())) overallStatus = "GREEN";
            else if ("YELLOW".equals(result.getStatus()) && "RED".equals(overallStatus)) overallStatus = "YELLOW";
        }
        public Long getMedicineId() { return medicineId; }
        public String getMedicineName() { return medicineName; }
        public String getCategory() { return category; }
        public String getImageUrl() { return imageUrl; }
        public String getFallbackImageUrl() { return fallbackImageUrl; }
        public int getHospitalCount() { return hospitalCount; }
        public int getAvailableHospitalCount() { return availableHospitalCount; }
        public String getOverallStatus() { return overallStatus; }
    }

    public static class MedicineAvailability {
        private final String hospitalName;
        private final String district;
        private final int quantity;
        private final int threshold;
        private final java.time.LocalDate lastUpdated;
        private final String status;

        public MedicineAvailability(String hospitalName, String district, int quantity, int threshold,
                                    java.time.LocalDate lastUpdated, String status) {
            this.hospitalName = hospitalName;
            this.district = district;
            this.quantity = quantity;
            this.threshold = threshold;
            this.lastUpdated = lastUpdated;
            this.status = status;
        }
        public String getHospitalName() { return hospitalName; }
        public String getDistrict() { return district; }
        public int getQuantity() { return quantity; }
        public int getThreshold() { return threshold; }
        public java.time.LocalDate getLastUpdated() { return lastUpdated; }
        public String getStatus() { return status; }
    }

    private List<MedicineCard> medicineCards(List<SearchResult> results) {
        Map<Long, MedicineCard> grouped = new LinkedHashMap<>();
        for (SearchResult result : results) {
            if (result.getMedicineId() == null) continue;
            grouped.computeIfAbsent(result.getMedicineId(),
                    id -> new MedicineCard(id, result.getMedicineName(), result.getCategory())).add(result);
        }
        return new ArrayList<>(grouped.values());
    }

    private List<String> districts() {
        try {
            return hospitalRepository.findAll().stream().map(hospital -> hospital.getDistrict()).distinct().sorted().toList();
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            return local == null ? List.of() : local.districts();
        }
    }

    private Map<String, String> districtStatuses(List<SearchResult> results) {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (SearchResult result : results) {
            String current = statuses.get(result.getDistrict());
            StockStatus candidate = StockStatus.from(result.getQuantity(), result.getThreshold());
            if (current == null || statusRank(candidate) > statusRank(StockStatus.valueOf(current))) {
                statuses.put(result.getDistrict(), candidate.name());
            }
        }
        return statuses;
    }

    private int statusRank(StockStatus status) {
        if (status == StockStatus.RED) return 3;
        if (status == StockStatus.YELLOW) return 2;
        return 1;
    }
}
