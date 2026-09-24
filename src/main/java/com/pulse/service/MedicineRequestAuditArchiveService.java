package com.pulse.service;

import com.pulse.local.model.LocalMedicineRequestActionLog;
import com.pulse.local.model.LocalMedicineRequestAuditArchive;
import com.pulse.local.repository.LocalMedicineRequestActionLogRepository;
import com.pulse.local.repository.LocalMedicineRequestAuditArchiveRepository;
import com.pulse.model.Hospital;
import com.pulse.model.MedicineRequestActionLog;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.DistrictRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.MedicineRequestActionLogRepository;
import com.pulse.model.MedicineRequestAuditArchive;
import com.pulse.repository.MedicineRequestAuditArchiveRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Retains the administrative medicine-request decision trail separately from
 * stock movement history. Hot rows remain queryable for the configured cold
 * window; monthly archives are compressed, AES-GCM encrypted and stored both
 * locally and in cloud PostgreSQL. Archived content is only decrypted when an
 * authorized administrator explicitly opens an archive period.
 */
@Service
public class MedicineRequestAuditArchiveService {
    private static final String GRANULARITY = "MONTHLY";
    private final MedicineRequestActionLogRepository cloudLogs;
    private final ObjectProvider<LocalMedicineRequestActionLogRepository> localLogsProvider;
    private final MedicineRequestAuditArchiveRepository cloudArchives;
    private final ObjectProvider<LocalMedicineRequestAuditArchiveRepository> localArchivesProvider;
    private final HospitalRepository hospitals;
    private final DistrictRepository districts;
    private final MedicineRepository medicines;
    private final MedicineRequestSchemaService schema;
    private final AuditArchiveService crypto;
    private final ObjectMapper objectMapper;
    private final Path archiveRoot;
    private final int coldAfterDays;
    private final int retentionDays;

    public MedicineRequestAuditArchiveService(
            MedicineRequestActionLogRepository cloudLogs,
            ObjectProvider<LocalMedicineRequestActionLogRepository> localLogsProvider,
            MedicineRequestAuditArchiveRepository cloudArchives,
            ObjectProvider<LocalMedicineRequestAuditArchiveRepository> localArchivesProvider,
            HospitalRepository hospitals,
            DistrictRepository districts,
            MedicineRepository medicines,
            MedicineRequestSchemaService schema,
            AuditArchiveService crypto,
            ObjectMapper objectMapper,
            @Value("${pulse.audit.archive-root:./data/audit-archives}") String archiveRoot,
            @Value("${pulse.request-audit.cold-after-days:90}") int coldAfterDays,
            @Value("${pulse.request-audit.retention-days:365}") int retentionDays) {
        this.cloudLogs = cloudLogs;
        this.localLogsProvider = localLogsProvider;
        this.cloudArchives = cloudArchives;
        this.localArchivesProvider = localArchivesProvider;
        this.hospitals = hospitals;
        this.districts = districts;
        this.medicines = medicines;
        this.schema = schema;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.archiveRoot = Path.of(archiveRoot).toAbsolutePath().normalize().resolve("request-decisions");
        this.coldAfterDays = Math.max(30, coldAfterDays);
        this.retentionDays = Math.max(this.coldAfterDays, retentionDays);
    }

    public int getColdAfterDays() { return coldAfterDays; }
    public int getRetentionDays() { return retentionDays; }

    public List<DecisionView> hotForHospital(Long hospitalId) {
        return fromCloudOrLocal(hospitalId, LocalDate.now().minusDays(coldAfterDays - 1L), LocalDate.now());
    }

    public List<DecisionView> hotForDistrict(Long districtId) {
        return scopeHot(hospitals.findAll().stream().filter(h -> Objects.equals(h.getDistrictId(), districtId)).map(Hospital::getHospitalId).toList());
    }

    public List<DecisionView> hotForState(Long stateId) {
        // State scope is resolved from the district relation rather than trusting a request parameter.
        Set<Long> districtIds = districts.findByStateIdOrderByName(stateId).stream()
                .map(com.pulse.model.District::getDistrictId).collect(java.util.stream.Collectors.toSet());
        List<Long> ids = hospitals.findAll().stream().filter(h -> districtIds.contains(h.getDistrictId()))
                .map(Hospital::getHospitalId).toList();
        return scopeHot(ids);
    }

    /** Returns monthly archive periods for the supplied hospital scope. */
    public List<ArchiveSummary> archivesForHospitals(Collection<Long> hospitalIds) {
        Map<String, ArchiveSummary> merged = new HashMap<>();
        LocalMedicineRequestAuditArchiveRepository local = localArchivesProvider.getIfAvailable();
        if (local != null) {
            for (LocalMedicineRequestAuditArchive a : local.findAll()) {
                if (!hospitalIds.contains(a.getHospitalId())) continue;
                merged.put(key(a.getHospitalId(), a.getPeriodStart()), new ArchiveSummary(a.getHospitalId(), a.getPeriodStart(), a.getPeriodEnd(), a.getStorageName(), a.getSha256(), "LOCAL"));
            }
        }
        try {
            for (MedicineRequestAuditArchive a : cloudArchives.findAll()) {
                if (!hospitalIds.contains(a.getHospitalId())) continue;
                merged.putIfAbsent(key(a.getHospitalId(), a.getPeriodStart()), new ArchiveSummary(a.getHospitalId(), a.getPeriodStart(), a.getPeriodEnd(), a.getStorageName(), a.getSha256(), "CLOUD"));
            }
        } catch (RuntimeException ignored) { }
        return merged.values().stream().sorted(Comparator.comparing(ArchiveSummary::periodStart).reversed()).toList();
    }

    public List<ArchivePeriod> periodsForHospitals(Collection<Long> hospitalIds) {
        Map<LocalDate, LocalDate> periods = new TreeMap<>(Comparator.reverseOrder());
        for (ArchiveSummary a : archivesForHospitals(hospitalIds)) periods.put(a.periodStart(), a.periodEnd());
        return periods.entrySet().stream().map(e -> new ArchivePeriod(e.getKey(), e.getValue())).toList();
    }

    public List<DecisionView> openArchiveForHospital(Long hospitalId, LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException("Invalid archive period.");
        if (end.isAfter(LocalDate.now())) throw new IllegalArgumentException("Future archive periods are not valid.");
        Map<String, DecisionView> merged = new LinkedHashMap<>();
        for (ArchiveSummary summary : archivesForHospitals(List.of(hospitalId))) {
            if (summary.periodEnd().isBefore(start) || summary.periodStart().isAfter(end)) continue;
            for (DecisionView row : readArchive(summary)) merged.put(row.eventId(), row);
        }
        return merged.values().stream().filter(r -> !r.occurredAt().toLocalDate().isBefore(start) && !r.occurredAt().toLocalDate().isAfter(end))
                .sorted(Comparator.comparing(DecisionView::occurredAt).reversed()
                        .thenComparing(DecisionView::eventId, Comparator.reverseOrder())).toList();
    }

    public List<DecisionView> openArchiveForHospitals(Collection<Long> hospitalIds, LocalDate start, LocalDate end) {
        Map<String, DecisionView> merged = new LinkedHashMap<>();
        for (Long hospitalId : hospitalIds) {
            for (DecisionView row : openArchiveForHospital(hospitalId, start, end)) merged.put(row.eventId(), row);
        }
        return merged.values().stream().sorted(Comparator.comparing(DecisionView::occurredAt).reversed()
                .thenComparing(DecisionView::eventId, Comparator.reverseOrder())).toList();
    }

    @Scheduled(cron = "${pulse.request-audit.monthly-cron:0 45 3 1 * *}")
    public void monthlyArchiveJob() {
        if (!crypto.archiveEncryptionReady()) return;
        YearMonth lastClosedMonth = YearMonth.now().minusMonths(1);
        YearMonth firstCatchUpMonth = lastClosedMonth.minusMonths(11);
        for (Hospital hospital : safeHospitals()) {
            for (YearMonth month = firstCatchUpMonth; !month.isAfter(lastClosedMonth); month = month.plusMonths(1)) {
                try { createMonthlyArchive(hospital.getHospitalId(), month); } catch (RuntimeException ignored) { }
            }
        }
        syncLocalArchivesToCloud();
        purgeOldHotRows(LocalDate.now().minusDays(coldAfterDays));
        purgeExpiredArchives(LocalDate.now().minusDays(retentionDays));
    }

    public byte[] exportExcelForHospital(Long hospitalId, LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException("Invalid export period.");
        if (end.isAfter(LocalDate.now())) throw new IllegalArgumentException("Future export periods are not valid.");
        List<DecisionView> rows = new ArrayList<>(hotForHospital(hospitalId).stream()
                .filter(r -> !r.occurredAt().toLocalDate().isBefore(start) && !r.occurredAt().toLocalDate().isAfter(end))
                .toList());
        rows.addAll(openArchiveForHospital(hospitalId, start, end));
        Map<String, DecisionView> unique = new LinkedHashMap<>();
        rows.forEach(r -> unique.put(r.eventId(), r));
        List<DecisionView> ordered = unique.values().stream()
                .sorted(Comparator.comparing(DecisionView::occurredAt).reversed()
                .thenComparing(DecisionView::eventId, Comparator.reverseOrder()))
                .toList();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Request decisions");
            String[] headers = {"Date","Action","Medicine","Hospital","Requested","Fulfilled","Result","Actor","Role","Tier","Note","Event ID","Hash"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int rowIndex = 1;
            for (DecisionView r : ordered) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(r.occurredAt().toString());
                row.createCell(1).setCellValue(r.action());
                row.createCell(2).setCellValue(r.medicineName());
                row.createCell(3).setCellValue(r.hospitalName());
                row.createCell(4).setCellValue(r.requestedQuantity());
                row.createCell(5).setCellValue(r.fulfilledQuantity());
                row.createCell(6).setCellValue(r.toStatus() == null ? "" : r.toStatus());
                row.createCell(7).setCellValue(r.actorUsername());
                row.createCell(8).setCellValue(r.actorRole());
                row.createCell(9).setCellValue(r.tier());
                row.createCell(10).setCellValue(r.note() == null ? "" : r.note());
                row.createCell(11).setCellValue(r.eventId());
                row.createCell(12).setCellValue(r.hash());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create the request decision Excel workbook.", ex);
        }
    }

    public String sealMonthNow(Long hospitalId, YearMonth month) {
        if (!crypto.archiveEncryptionReady()) throw new IllegalStateException("Audit archive encryption key is not configured.");
        ArchiveResult result = createMonthlyArchive(hospitalId, month == null ? YearMonth.now().minusMonths(1) : month);
        syncLocalArchivesToCloud();
        return result == null ? "No request decisions were found for that month." : "Sealed " + result.entries() + " request decisions into " + result.fileName();
    }

    private ArchiveResult createMonthlyArchive(Long hospitalId, YearMonth month) {
        LocalDate start = month.atDay(1), end = month.atEndOfMonth();
        if (archiveExists(hospitalId, start)) return null;
        List<DecisionView> rows = rawViews(hospitalId, start, end);
        if (rows.isEmpty()) return null;
        try {
            byte[] plain = objectMapper.writeValueAsBytes(rows);
            byte[] encrypted = crypto.encryptArchiveBytes(plain);
            String sha = sha256(encrypted);
            Files.createDirectories(archiveRoot.resolve(String.valueOf(hospitalId)));
            String fileName = "request-decisions-" + hospitalId + "-" + start + ".pulse-log";
            Path file = archiveRoot.resolve(String.valueOf(hospitalId)).resolve(fileName);
            Files.write(file, encrypted);
            LocalMedicineRequestAuditArchiveRepository local = localArchivesProvider.getIfAvailable();
            if (local != null) {
                LocalMedicineRequestAuditArchive meta = new LocalMedicineRequestAuditArchive();
                meta.setHospitalId(hospitalId); meta.setGranularity(GRANULARITY); meta.setPeriodStart(start); meta.setPeriodEnd(end);
                meta.setStorageName(file.toString()); meta.setSha256(sha); meta.setCreatedAt(LocalDateTime.now()); local.saveAndFlush(meta);
            }
            return new ArchiveResult(fileName, encrypted, rows.size());
        } catch (IOException ex) { throw new IllegalStateException("Unable to write request decision archive.", ex); }
    }

    private List<DecisionView> rawViews(Long hospitalId, LocalDate start, LocalDate end) {
        Map<String, DecisionView> merged = new LinkedHashMap<>();
        try {
            schema.ensureTableOrThrow();
            for (MedicineRequestActionLog l : cloudLogs.findByHospitalIdAndOccurredAtBetweenOrderByOccurredAtAsc(hospitalId, start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1))) {
                merged.put(l.getEventId(), toView(l));
            }
        } catch (RuntimeException ignored) { }
        LocalMedicineRequestActionLogRepository local = localLogsProvider.getIfAvailable();
        if (local != null) {
            for (LocalMedicineRequestActionLog l : local.findByHospitalIdAndOccurredAtBetweenOrderByOccurredAtAsc(hospitalId, start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1))) {
                merged.putIfAbsent(l.getEventId(), toView(l));
            }
        }
        return merged.values().stream().sorted(Comparator.comparing(DecisionView::occurredAt).reversed()
                .thenComparing(DecisionView::eventId, Comparator.reverseOrder())).toList();
    }

    private List<DecisionView> fromCloudOrLocal(Long hospitalId, LocalDate start, LocalDate end) {
        return rawViews(hospitalId, start, end);
    }

    private List<DecisionView> scopeHot(Collection<Long> hospitalIds) {
        Map<String, DecisionView> merged = new LinkedHashMap<>();
        for (Long id : hospitalIds) for (DecisionView v : hotForHospital(id)) merged.put(v.eventId(), v);
        return merged.values().stream().sorted(Comparator.comparing(DecisionView::occurredAt).reversed()).toList();
    }

    private DecisionView toView(MedicineRequestActionLog l) {
        return new DecisionView(l.getEventId(), l.getRequestId(), l.getHospitalId(), l.getMedicineId(), hospitalName(l.getHospitalId()),
                medicineName(l.getMedicineId()), l.getActorRole(), l.getActorUsername(), l.getTier(), l.getAction(), l.getFromStatus(), l.getToStatus(),
                l.getRequestedQuantity(), l.getFulfilledQuantity(), l.getNote(), l.getOccurredAt(), l.getPreviousHash(), l.getHash());
    }

    private DecisionView toView(LocalMedicineRequestActionLog l) {
        return new DecisionView(l.getEventId(), l.getCloudRequestId() != null ? l.getCloudRequestId() : l.getLocalRequestId(), l.getHospitalId(), l.getMedicineId(),
                hospitalName(l.getHospitalId()), medicineName(l.getMedicineId()), l.getActorRole(), l.getActorUsername(), l.getTier(), l.getAction(),
                l.getFromStatus(), l.getToStatus(), l.getRequestedQuantity(), l.getFulfilledQuantity(), l.getNote(), l.getOccurredAt(), l.getPreviousHash(), l.getHash());
    }

    private DecisionView toView(ArchivedDecision row) {
        return new DecisionView(row.eventId(), row.requestId(), row.hospitalId(), row.medicineId(), row.hospitalName(), row.medicineName(), row.actorRole(),
                row.actorUsername(), row.tier(), row.action(), row.fromStatus(), row.toStatus(), row.requestedQuantity(), row.fulfilledQuantity(), row.note(),
                row.occurredAt(), row.previousHash(), row.hash());
    }

    private List<DecisionView> readArchive(ArchiveSummary summary) {
        byte[] payload = null;
        LocalMedicineRequestAuditArchiveRepository local = localArchivesProvider.getIfAvailable();
        if ("LOCAL".equals(summary.source()) && local != null) {
            var meta = local.findByHospitalIdAndGranularityAndPeriodStart(summary.hospitalId(), GRANULARITY, summary.periodStart()).orElse(null);
            if (meta != null) try { payload = Files.readAllBytes(Path.of(meta.getStorageName())); } catch (IOException ignored) { }
        }
        if (payload == null) {
            try { payload = cloudArchives.findByHospitalIdAndGranularityAndPeriodStart(summary.hospitalId(), GRANULARITY, summary.periodStart()).map(MedicineRequestAuditArchive::getEncryptedPayload).orElse(null); } catch (RuntimeException ignored) { }
        }
        if (payload == null || !sha256(payload).equals(summary.sha256())) throw new IllegalStateException("Request decision archive integrity check failed.");
        try {
            ArchivedDecision[] rows = objectMapper.readValue(crypto.decryptArchiveBytes(payload), ArchivedDecision[].class);
            return Arrays.stream(rows).map(this::toView).toList();
        } catch (RuntimeException ex) { throw new IllegalStateException("Unable to open request decision archive.", ex); }
    }

    private void syncLocalArchivesToCloud() {
        LocalMedicineRequestAuditArchiveRepository local = localArchivesProvider.getIfAvailable();
        if (local == null) return;
        for (LocalMedicineRequestAuditArchive meta : local.findAll()) {
            try {
                if (cloudArchives.findByHospitalIdAndGranularityAndPeriodStart(meta.getHospitalId(), GRANULARITY, meta.getPeriodStart()).isPresent()) continue;
                byte[] payload = Files.readAllBytes(Path.of(meta.getStorageName()));
                if (!sha256(payload).equals(meta.getSha256())) continue;
                MedicineRequestAuditArchive a = new MedicineRequestAuditArchive();
                a.setHospitalId(meta.getHospitalId()); a.setGranularity(GRANULARITY); a.setPeriodStart(meta.getPeriodStart()); a.setPeriodEnd(meta.getPeriodEnd());
                a.setStorageName(Path.of(meta.getStorageName()).getFileName().toString()); a.setSha256(meta.getSha256()); a.setCreatedAt(meta.getCreatedAt()); a.setEncryptedPayload(payload);
                cloudArchives.saveAndFlush(a);
            } catch (RuntimeException | IOException ignored) { }
        }
    }

    private void purgeOldHotRows(LocalDate cutoff) {
        // Never delete a hot decision row merely because it is old. Its closed
        // month must already have a verified encrypted archive. This prevents
        // a missed scheduler run or cloud outage from becoming data loss.
        LocalMedicineRequestActionLogRepository local = localLogsProvider.getIfAvailable();
        if (local != null) {
            try {
                List<LocalMedicineRequestActionLog> candidates = local.findAll().stream()
                        .filter(l -> !l.isPendingSync() && l.getOccurredAt() != null && l.getOccurredAt().isBefore(cutoff.atStartOfDay()))
                        .toList();
                List<Long> safeIds = candidates.stream().filter(l -> archiveExists(l.getHospitalId(), YearMonth.from(l.getOccurredAt().toLocalDate()).atDay(1)))
                        .map(LocalMedicineRequestActionLog::getActionLogId).toList();
                if (!safeIds.isEmpty()) local.deleteAllByIdInBatch(safeIds);
            } catch (RuntimeException ignored) { }
        }
        try {
            schema.ensureTableOrThrow();
            List<MedicineRequestActionLog> candidates = cloudLogs.findAll().stream()
                    .filter(l -> l.getOccurredAt() != null && l.getOccurredAt().isBefore(cutoff.atStartOfDay()))
                    .toList();
            List<Long> safeIds = candidates.stream().filter(l -> archiveExists(l.getHospitalId(), YearMonth.from(l.getOccurredAt().toLocalDate()).atDay(1)))
                    .map(MedicineRequestActionLog::getActionLogId).toList();
            if (!safeIds.isEmpty()) cloudLogs.deleteAllByIdInBatch(safeIds);
        } catch (RuntimeException ignored) { }
    }

    private void purgeExpiredArchives(LocalDate cutoff) {
        LocalMedicineRequestAuditArchiveRepository local = localArchivesProvider.getIfAvailable();
        if (local != null) {
            for (LocalMedicineRequestAuditArchive a : local.findAll()) if (a.getPeriodEnd().isBefore(cutoff)) {
                try { Files.deleteIfExists(Path.of(a.getStorageName())); } catch (IOException ignored) { }
                local.delete(a);
            }
        }
        try { for (MedicineRequestAuditArchive a : cloudArchives.findByPeriodEndBefore(cutoff)) cloudArchives.delete(a); } catch (RuntimeException ignored) { }
    }

    private boolean archiveExists(Long hospitalId, LocalDate periodStart) {
        LocalMedicineRequestAuditArchiveRepository local = localArchivesProvider.getIfAvailable();
        if (local != null) {
            var meta = local.findByHospitalIdAndGranularityAndPeriodStart(hospitalId, GRANULARITY, periodStart).orElse(null);
            if (meta != null) {
                try {
                    byte[] payload = Files.readAllBytes(Path.of(meta.getStorageName()));
                    if (sha256(payload).equals(meta.getSha256())) return true;
                } catch (IOException ignored) { }
            }
        }
        try {
            return cloudArchives.findByHospitalIdAndGranularityAndPeriodStart(hospitalId, GRANULARITY, periodStart)
                    .map(a -> a.getEncryptedPayload() != null && a.getEncryptedPayload().length > 0 && sha256(a.getEncryptedPayload()).equals(a.getSha256()))
                    .orElse(false);
        } catch (RuntimeException ignored) { return false; }
    }

    private List<Hospital> safeHospitals() { try { return hospitals.findAll(); } catch (RuntimeException ex) { return List.of(); } }
    private String hospitalName(Long id) { return hospitals.findById(id).map(Hospital::getName).orElse("Hospital #" + id); }
    private String medicineName(Long id) { return medicines.findById(id).map(com.pulse.model.Medicine::getName).orElse("Medicine #" + id); }
    private static String key(Long hospitalId, LocalDate start) { return hospitalId + "|" + start; }
    private static String sha256(byte[] data) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record DecisionView(String eventId, Long requestId, Long hospitalId, Long medicineId, String hospitalName, String medicineName,
                               String actorRole, String actorUsername, String tier, String action, String fromStatus, String toStatus,
                               int requestedQuantity, int fulfilledQuantity, String note, LocalDateTime occurredAt, String previousHash, String hash) {}
    public record ArchiveSummary(Long hospitalId, LocalDate periodStart, LocalDate periodEnd, String storageName, String sha256, String source) {}
    public record ArchivePeriod(LocalDate periodStart, LocalDate periodEnd) {}
    private record ArchiveResult(String fileName, byte[] payload, int entries) {}
    private record ArchivedDecision(String eventId, Long requestId, Long hospitalId, Long medicineId, String hospitalName, String medicineName,
                                    String actorRole, String actorUsername, String tier, String action, String fromStatus, String toStatus,
                                    int requestedQuantity, int fulfilledQuantity, String note, LocalDateTime occurredAt, String previousHash, String hash) {}
}
