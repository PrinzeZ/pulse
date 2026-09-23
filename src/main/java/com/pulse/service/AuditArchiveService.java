package com.pulse.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.pulse.local.model.LocalAuditArchive;
import com.pulse.local.model.LocalAuditAccessRequest;
import com.pulse.local.model.LocalStockMovement;
import com.pulse.local.repository.LocalAuditAccessRequestRepository;
import com.pulse.local.repository.LocalAuditArchiveRepository;
import com.pulse.local.repository.LocalStockMovementRepository;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.AuditArchive;
import com.pulse.model.Medicine;
import com.pulse.model.StockMovement;
import com.pulse.repository.AuditArchiveRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockMovementRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class AuditArchiveService {
    private static final String MAGIC = "PULSE-AUDIT-V1";
    private static final int GCM_TAG_BITS = 128;

    private final ObjectProvider<LocalStockMovementRepository> localLedgerProvider;
    private final ObjectProvider<LocalAuditArchiveRepository> localArchiveProvider;
    private final ObjectProvider<LocalAuditAccessRequestRepository> localRequestProvider;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final StockMovementRepository cloudLedger;
    private final AuditArchiveRepository cloudArchives;
    private final HospitalRepository hospitals;
    private final MedicineRepository medicines;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    private final AuditNotificationService notificationService;
    private final Path archiveRoot;
    private final int hotDays;
    private final int coldAfterDays;
    private final int retentionDays;
    private final byte[] key;

    public AuditArchiveService(
            ObjectProvider<LocalStockMovementRepository> localLedgerProvider,
            ObjectProvider<LocalAuditArchiveRepository> localArchiveProvider,
            ObjectProvider<LocalAuditAccessRequestRepository> localRequestProvider,
            ObjectProvider<LocalOfflineStore> localStoreProvider,
            StockMovementRepository cloudLedger,
            AuditArchiveRepository cloudArchives,
            HospitalRepository hospitals,
            MedicineRepository medicines,
            ObjectMapper objectMapper,
            @Value("${pulse.audit.archive-root:./data/audit-archives}") String archiveRoot,
            @Value("${pulse.audit.hot-days:30}") int hotDays,
            @Value("${pulse.audit.cold-after-days:90}") int coldAfterDays,
            @Value("${pulse.audit.retention-days:365}") int retentionDays,
            @Value("${PULSE_AUDIT_ARCHIVE_KEY_BASE64:}") String configuredKey,
            @Value("${PULSE_LAN_REGISTRATION_TOKEN:}") String fallbackSecret,
            AuditNotificationService notificationService) {
        this.localLedgerProvider = localLedgerProvider;
        this.localArchiveProvider = localArchiveProvider;
        this.localRequestProvider = localRequestProvider;
        this.localStoreProvider = localStoreProvider;
        this.cloudLedger = cloudLedger;
        this.cloudArchives = cloudArchives;
        this.hospitals = hospitals;
        this.medicines = medicines;
        this.objectMapper = objectMapper;
        this.archiveRoot = Path.of(archiveRoot).toAbsolutePath().normalize();
        this.hotDays = Math.max(1, hotDays);
        this.coldAfterDays = Math.max(this.hotDays, coldAfterDays);
        this.retentionDays = Math.max(this.coldAfterDays, retentionDays);
        this.key = decodeKeyOrDerive(configuredKey, fallbackSecret);
        this.notificationService = notificationService;
    }

    public boolean archiveEncryptionReady() { return key != null; }
    public int getHotDays() { return hotDays; }
    public int getColdAfterDays() { return coldAfterDays; }
    public int getRetentionDays() { return retentionDays; }

    public List<AuditRow> recentForStaff(Long hospitalId) {
        LocalDate today = LocalDate.now();
        return history(hospitalId, today.minusDays(hotDays - 1L), today);
    }

    public List<AuditRow> history(Long hospitalId, LocalDate start, LocalDate end) {
        LocalDate safeStart = start == null ? end : start;
        LocalDate safeEnd = end == null ? safeStart : end;
        if (safeEnd == null || safeEnd.isBefore(safeStart)) return List.of();

        /*
         * The cloud ledger is the durable source of truth for audit history.
         * The local ledger is an offline cache and may be newer while a sync is
         * pending. Merge both instead of choosing one. This is important when a
         * hospital restarts from a fresh application folder: the local H2 file
         * may be new/empty, but the already-synchronised cloud history must still
         * be visible to staff and administrators.
         */
        Map<String, AuditRow> merged = new LinkedHashMap<>();

        try {
            cloudLedger.findByHospitalIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscMovementIdAsc(
                            hospitalId, safeStart.atStartOfDay(), safeEnd.plusDays(1).atStartOfDay())
                    .stream().map(this::toRow).forEach(r -> merged.put(r.eventId(), r));
        } catch (RuntimeException ignored) {
            // Offline: local cache below remains usable.
        }

        LocalStockMovementRepository local = localLedgerProvider.getIfAvailable();
        if (local != null) {
            local.findByHospitalIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscLocalMovementIdAsc(
                            hospitalId, safeStart.atStartOfDay(), safeEnd.plusDays(1).atStartOfDay())
                    .stream().map(this::toRow).forEach(r -> merged.put(r.eventId(), r));
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(AuditRow::occurredAt).thenComparing(AuditRow::eventId))
                .toList();
    }

    public List<ArchiveSummary> archives(Long hospitalId) {
        Map<String, ArchiveSummary> merged = new LinkedHashMap<>();
        LocalAuditArchiveRepository local = localArchiveProvider.getIfAvailable();
        if (local != null) {
            for (LocalAuditArchive a : local.findByHospitalIdOrderByPeriodStartDesc(hospitalId)) {
                merged.put(key(a.getGranularity(), a.getPeriodStart()), new ArchiveSummary(
                        a.getArchiveId(), a.getGranularity(), a.getPeriodStart(), a.getPeriodEnd(), a.getStorageName(), a.getSha256(), "LOCAL"));
            }
        }
        try {
            for (AuditArchive a : cloudArchives.findByHospitalIdOrderByPeriodStartDesc(hospitalId)) {
                merged.putIfAbsent(key(a.getGranularity(), a.getPeriodStart()), new ArchiveSummary(
                        a.getArchiveId(), a.getGranularity(), a.getPeriodStart(), a.getPeriodEnd(), a.getStorageName(), a.getSha256(), "CLOUD"));
            }
        } catch (RuntimeException ignored) { }
        return merged.values().stream().sorted(Comparator.comparing(ArchiveSummary::periodStart).reversed()).toList();
    }

    /** Creates an immutable, encrypted medicine-specific evidence workbook for a resupply request. */
    public RequestAuditAttachment createMedicineRequestAttachment(Long hospitalId, Long medicineId, LocalDate day) {
        if (!archiveEncryptionReady()) {
            throw new IllegalStateException("Audit encryption key is not configured; the medicine evidence file cannot be created.");
        }
        LocalDate safeDay = day == null ? LocalDate.now() : day;
        LocalDate start = safeDay.minusDays(29);
        List<AuditRow> monthRows = reportRows(hospitalId, start, safeDay, false).stream()
                .filter(r -> medicineId.equals(r.medicineId())).toList();
        List<AuditRow> todayRows = monthRows.stream()
                .filter(r -> safeDay.equals(r.occurredAt().toLocalDate())).toList();
        byte[] workbook = medicineRequestExcel(todayRows, monthRows, medicineName(medicineId), safeDay);
        byte[] encrypted = encryptFile(workbook);
        String fileName = "pulse-medicine-audit-" + medicineId + "-" + safeDay + ".xlsx.pulse-proof";
        return new RequestAuditAttachment(fileName, sha256(encrypted), start, safeDay, LocalDateTime.now(), encrypted);
    }

    public byte[] decryptRequestAttachment(byte[] encryptedPayload, String expectedSha256) {
        if (encryptedPayload == null || encryptedPayload.length == 0) throw new IllegalArgumentException("Audit evidence file is unavailable.");
        if (expectedSha256 != null && !expectedSha256.equals(sha256(encryptedPayload))) {
            throw new IllegalStateException("Audit evidence integrity check failed.");
        }
        return decryptFile(encryptedPayload);
    }

    /** Read the immutable medicine evidence workbook for browser-only district/state review. */
    public RequestEvidenceView requestEvidenceView(byte[] encryptedPayload, String expectedSha256) {
        byte[] workbookBytes = decryptRequestAttachment(encryptedPayload, expectedSha256);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            List<AuditRow> today = readAuditSheet(workbook, 0);
            List<AuditRow> month = workbook.getNumberOfSheets() > 1 ? readAuditSheet(workbook, 1) : today;
            String medicineName = month.isEmpty() ? (today.isEmpty() ? "Medicine audit" : today.get(0).medicineName()) : month.get(0).medicineName();
            return new RequestEvidenceView(medicineName, today, month);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to open the medicine audit evidence.", ex);
        }
    }

    private List<AuditRow> readAuditSheet(XSSFWorkbook workbook, int index) {
        if (index >= workbook.getNumberOfSheets()) return List.of();
        var sheet = workbook.getSheetAt(index);
        List<AuditRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null) continue;
            try {
                LocalDateTime occurredAt = LocalDateTime.parse(row.getCell(0).getStringCellValue());
                String medicine = row.getCell(1).getStringCellValue();
                Long medicineId = (long) row.getCell(2).getNumericCellValue();
                Integer before = row.getCell(3).getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC ? (int) row.getCell(3).getNumericCellValue() : null;
                int delta = (int) row.getCell(4).getNumericCellValue();
                Integer after = row.getCell(5).getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC ? (int) row.getCell(5).getNumericCellValue() : null;
                String type = row.getCell(6).getStringCellValue();
                String reference = row.getCell(7).getStringCellValue();
                String actor = row.getCell(8).getStringCellValue();
                String note = row.getCell(9).getStringCellValue();
                String eventId = row.getCell(10).getStringCellValue();
                String hash = row.getCell(11).getStringCellValue();
                rows.add(new AuditRow(eventId, null, medicineId, medicine, before, delta, after, type, reference, null, actor, note, occurredAt, hash, null));
            } catch (RuntimeException ignored) { }
        }
        return rows;
    }

    public boolean hasArchiveKey() { return archiveEncryptionReady(); }

    public byte[] exportExcel(Long hospitalId, LocalDate start, LocalDate end, boolean allowArchived) {
        return exportExcel(hospitalId, start, end, allowArchived, null);
    }

    public byte[] exportExcel(Long hospitalId, LocalDate start, LocalDate end, boolean allowArchived, Long medicineId) {
        List<AuditRow> rows = reportRows(hospitalId, start, end, allowArchived);
        List<AuditRow> medicineRows = medicineId == null ? List.of()
                : reportRows(hospitalId, end.minusDays(29), end, allowArchived).stream()
                    .filter(r -> medicineId.equals(r.medicineId()))
                    .toList();
        return excel(rows, medicineRows, start, end, medicineId == null ? null : medicineName(medicineId));
    }

    /**
     * The hot ledger is always readable for the normal window. Archived data is
     * deliberately unlocked only by an explicit report request (admin selection
     * or an approved staff historical request). Listing archives never decrypts them.
     */
    public List<AuditRow> reportRows(Long hospitalId, LocalDate start, LocalDate end, boolean unlockArchived) {
        Map<String, AuditRow> merged = new LinkedHashMap<>();
        for (AuditRow row : history(hospitalId, start, end)) merged.put(row.eventId(), row);
        if (unlockArchived) {
            for (AuditRow row : archivedHistory(hospitalId, start, end)) merged.putIfAbsent(row.eventId(), row);
        }
        return merged.values().stream().sorted(Comparator.comparing(AuditRow::occurredAt)).toList();
    }

    public List<AuditRow> archivedHistory(Long hospitalId, LocalDate start, LocalDate end) {
        List<AuditRow> all = new ArrayList<>();
        for (ArchiveSummary summary : archives(hospitalId)) {
            if (summary.periodEnd().isBefore(start) || summary.periodStart().isAfter(end)) continue;
            try { all.addAll(readArchive(summary, hospitalId)); } catch (RuntimeException ignored) { }
        }
        return all.stream().filter(r -> !r.occurredAt().toLocalDate().isBefore(start) && !r.occurredAt().toLocalDate().isAfter(end))
                .sorted(Comparator.comparing(AuditRow::occurredAt)).toList();
    }

    public long requestArchiveAccess(Long hospitalId, String username, LocalDate start, LocalDate end, String reason) {
        LocalAuditAccessRequestRepository repo = localRequestProvider.getIfAvailable();
        if (repo == null) throw new IllegalStateException("Archive approval is available on the hospital-local node.");
        if (end.isBefore(start)) throw new IllegalArgumentException("End date must be on or after start date.");
        LocalAuditAccessRequest r = new LocalAuditAccessRequest();
        r.setHospitalId(hospitalId); r.setRequesterUsername(username); r.setPeriodStart(start); r.setPeriodEnd(end);
        r.setReason(reason); r.setStatus("PENDING"); r.setCreatedAt(LocalDateTime.now());
        return repo.saveAndFlush(r).getRequestId();
    }

    public List<LocalAuditAccessRequest> pendingRequests(Long hospitalId) {
        LocalAuditAccessRequestRepository repo = localRequestProvider.getIfAvailable();
        return repo == null ? List.of() : repo.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
    }

    public void decideRequest(Long hospitalId, Long requestId, String adminUsername, boolean approve) {
        LocalAuditAccessRequestRepository repo = localRequestProvider.getIfAvailable();
        if (repo == null) throw new IllegalStateException("Archive approval is unavailable.");
        LocalAuditAccessRequest r = repo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Audit access request not found."));
        if (!hospitalId.equals(r.getHospitalId())) throw new IllegalArgumentException("Request is outside this hospital scope.");
        if (!"PENDING".equals(r.getStatus())) throw new IllegalArgumentException("This audit access request has already been reviewed.");
        r.setStatus(approve ? "APPROVED" : "REJECTED");
        r.setReviewedBy(adminUsername);
        r.setReviewedAt(LocalDateTime.now());
        repo.saveAndFlush(r);
    }

    public List<LocalAuditAccessRequest> requestsForStaff(Long hospitalId, String username) {
        LocalAuditAccessRequestRepository repo = localRequestProvider.getIfAvailable();
        if (repo == null) return List.of();
        return repo.findByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .filter(r -> username.equals(r.getRequesterUsername()))
                .toList();
    }

    public boolean approvedRequest(Long hospitalId, String username, LocalDate start, LocalDate end) {
        LocalAuditAccessRequestRepository repo = localRequestProvider.getIfAvailable();
        if (repo == null) return false;
        return repo.findByHospitalIdOrderByCreatedAtDesc(hospitalId).stream().anyMatch(r ->
                username.equals(r.getRequesterUsername()) && "APPROVED".equals(r.getStatus())
                        && !start.isBefore(r.getPeriodStart()) && !end.isAfter(r.getPeriodEnd()));
    }

    public String sealDayNow(Long hospitalId, LocalDate day) {
        if (!archiveEncryptionReady()) throw new IllegalStateException("Audit archive encryption key is not configured.");
        ArchiveResult result = createArchive(hospitalId, day, day, "DAILY");
        syncLocalArchivesToCloud();
        return result == null ? "Archive already exists or there are no movements for that date." : "Sealed " + result.entries() + " audit entries into " + result.fileName();
    }

    @Scheduled(cron = "${pulse.audit.daily-cron:0 15 2 * * *}")
    public void dailyArchiveJob() {
        if (!archiveEncryptionReady()) return;
        LocalDate day = LocalDate.now().minusDays(1);
        List<Long> ids = hospitalIds();
        for (Long id : ids) {
            try {
                ArchiveResult result = createArchive(id, day, day, "DAILY");
                if (result != null) notificationService.sendDailyArchiveNotice(id, day, result.payload(), result.fileName(), result.entries());
            } catch (RuntimeException ignored) { }
        }
        syncLocalArchivesToCloud();
    }

    @Scheduled(cron = "${pulse.audit.monthly-cron:0 30 3 1 * *}")
    public void monthlyArchiveJob() {
        if (!archiveEncryptionReady()) return;
        YearMonth month = YearMonth.now().minusMonths(1);
        for (Long id : hospitalIds()) {
            try {
                ArchiveResult monthly = createArchive(id, month.atDay(1), month.atEndOfMonth(), "MONTHLY");
                if (monthly != null || archiveExists(id, "MONTHLY", month.atDay(1))) compactDailyArchives(id, month);
            } catch (RuntimeException ignored) { }
        }
        syncLocalArchivesToCloud();
        purgeOldHotRows(LocalDate.now().minusDays(coldAfterDays));
        purgeExpiredArchives(LocalDate.now().minusDays(retentionDays));
    }

    private ArchiveResult createArchive(Long hospitalId, LocalDate start, LocalDate end, String granularity) {
        if (archiveExists(hospitalId, granularity, start)) return null;
        List<AuditRow> rows = history(hospitalId, start, end);
        if (rows.isEmpty()) return null;
        byte[] payload;
        try { payload = encrypt(rows); } catch (Exception ex) { throw new IllegalStateException("Unable to seal audit archive.", ex); }
        String name = "hospital-" + hospitalId + "-" + granularity.toLowerCase(Locale.ROOT) + "-" + start + ".pulse-audit";
        String sha = sha256(payload);
        writeLocalArchive(hospitalId, start, end, granularity, name, sha, payload);
        writeCloudArchive(hospitalId, start, end, granularity, name, sha, payload);
        return new ArchiveResult(name, payload, rows.size());
    }

    private void writeLocalArchive(Long hospitalId, LocalDate start, LocalDate end, String granularity, String name, String sha, byte[] payload) {
        LocalAuditArchiveRepository repo = localArchiveProvider.getIfAvailable();
        if (repo == null) return;
        try {
            Path dir = archiveRoot.resolve(String.valueOf(hospitalId));
            Files.createDirectories(dir);
            Path file = dir.resolve(name);
            Files.write(file, payload);
            LocalAuditArchive a = repo.findByHospitalIdAndGranularityAndPeriodStart(hospitalId, granularity, start).orElseGet(LocalAuditArchive::new);
            a.setHospitalId(hospitalId); a.setGranularity(granularity); a.setPeriodStart(start); a.setPeriodEnd(end); a.setStorageName(file.toString()); a.setSha256(sha); a.setCreatedAt(LocalDateTime.now());
            repo.saveAndFlush(a);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store local audit archive.", ex);
        }
    }

    private void writeCloudArchive(Long hospitalId, LocalDate start, LocalDate end, String granularity, String name, String sha, byte[] payload) {
        try {
            AuditArchive a = cloudArchives.findByHospitalIdAndGranularityAndPeriodStart(hospitalId, granularity, start).orElseGet(AuditArchive::new);
            a.setHospitalId(hospitalId); a.setGranularity(granularity); a.setPeriodStart(start); a.setPeriodEnd(end); a.setStorageName(name); a.setSha256(sha); a.setCreatedAt(LocalDateTime.now()); a.setEncryptedPayload(payload);
            cloudArchives.saveAndFlush(a);
        } catch (RuntimeException ignored) { }
    }

    private boolean archiveExists(Long hospitalId, String granularity, LocalDate start) {
        LocalAuditArchiveRepository local = localArchiveProvider.getIfAvailable();
        if (local != null) {
            var localArchive = local.findByHospitalIdAndGranularityAndPeriodStart(hospitalId, granularity, start).orElse(null);
            if (localArchive != null && Files.exists(Path.of(localArchive.getStorageName()))) return true;
        }
        try { return cloudArchives.findByHospitalIdAndGranularityAndPeriodStart(hospitalId, granularity, start).isPresent(); }
        catch (RuntimeException ex) { return false; }
    }

    private List<AuditRow> readArchive(ArchiveSummary summary, Long hospitalId) {
        byte[] payload = null;
        if ("LOCAL".equals(summary.source())) {
            try { payload = Files.readAllBytes(Path.of(summary.storageName())); } catch (IOException ignored) { }
        }
        if (payload == null) {
            try {
                AuditArchive a = cloudArchives.findByHospitalIdAndGranularityAndPeriodStart(hospitalId, summary.granularity(), summary.periodStart()).orElse(null);
                if (a != null) payload = a.getEncryptedPayload();
            } catch (RuntimeException ignored) { }
        }
        if (payload == null) throw new IllegalStateException("Archive unavailable.");
        if (!sha256(payload).equals(summary.sha256())) throw new IllegalStateException("Archive integrity check failed.");
        return decrypt(payload);
    }

    private byte[] encrypt(List<AuditRow> rows) throws Exception {
        byte[] plain = objectMapper.writeValueAsBytes(rows);
        java.util.zip.GZIPOutputStream gzip = null;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        gzip = new java.util.zip.GZIPOutputStream(compressed); gzip.write(plain); gzip.close();
        byte[] iv = new byte[12]; random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(compressed.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC.getBytes(StandardCharsets.US_ASCII)); out.write(0); out.write(iv); out.write(ciphertext);
        return out.toByteArray();
    }

    private List<AuditRow> decrypt(byte[] payload) {
        try {
            byte[] magic = MAGIC.getBytes(StandardCharsets.US_ASCII);
            if (payload.length < magic.length + 1 + 12 || !Arrays.equals(Arrays.copyOf(payload, magic.length), magic)) throw new IllegalArgumentException("Invalid P.U.L.S.E archive.");
            int offset = magic.length + 1; byte[] iv = Arrays.copyOfRange(payload, offset, offset + 12); byte[] cipherText = Arrays.copyOfRange(payload, offset + 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] compressed = cipher.doFinal(cipherText);
            java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
            byte[] plain = gzip.readAllBytes();
            return objectMapper.readValue(plain, new TypeReference<List<AuditRow>>() {});
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt audit archive.", ex); }
    }

    private byte[] medicineRequestExcel(List<AuditRow> todayRows, List<AuditRow> monthRows, String medicineName, LocalDate day) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeAuditSheet(workbook, "Today - " + day, todayRows);
            writeAuditSheet(workbook, "Medicine History (30d)", monthRows);
            workbook.getProperties().getCoreProperties().setTitle("P.U.L.S.E Medicine Audit Evidence - " + medicineName);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("Unable to create medicine audit evidence.", ex); }
    }

    private byte[] encryptFile(byte[] plain) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write("PULSE-AUDIT-FILE-V1".getBytes(StandardCharsets.US_ASCII)); out.write(0); out.write(iv); out.write(ciphertext);
            return out.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt medicine audit evidence.", ex); }
    }

    private byte[] decryptFile(byte[] payload) {
        try {
            byte[] magic = "PULSE-AUDIT-FILE-V1".getBytes(StandardCharsets.US_ASCII);
            if (payload.length < magic.length + 1 + 12 || !Arrays.equals(Arrays.copyOf(payload, magic.length), magic)) {
                throw new IllegalArgumentException("Invalid P.U.L.S.E audit evidence file.");
            }
            int offset = magic.length + 1;
            byte[] iv = Arrays.copyOfRange(payload, offset, offset + 12);
            byte[] ciphertext = Arrays.copyOfRange(payload, offset + 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception ex) { throw new IllegalStateException("Unable to open medicine audit evidence.", ex); }
    }

    private byte[] excel(List<AuditRow> rows, List<AuditRow> medicineRows, LocalDate start, LocalDate end, String medicineName) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeAuditSheet(workbook, "Stock Audit", rows);
            if (medicineName != null) {
                writeAuditSheet(workbook, "Medicine History (30d)", medicineRows);
            }
            workbook.getProperties().getCoreProperties().setTitle("P.U.L.S.E Stock Audit");
            workbook.write(out); return out.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("Unable to create Excel audit report.", ex); }
    }

    private void writeAuditSheet(XSSFWorkbook workbook, String name, List<AuditRow> rows) {
        var sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        String[] headers = {"Timestamp", "Medicine", "Medicine ID", "Quantity before", "Change", "Quantity after", "Movement type", "Reference", "Actor", "Note", "Event ID", "Hash"};
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        int rowNum = 1;
        for (AuditRow r : rows) {
            Row row = sheet.createRow(rowNum++);
            Object[] values = {r.occurredAt().toString(), r.medicineName(), r.medicineId(), r.quantityBefore(), r.deltaQuantity(), r.quantityAfter(), r.movementType(),
                    (r.referenceType() == null ? "" : r.referenceType()) + (r.referenceId() == null ? "" : " #" + r.referenceId()),
                    r.actorUsername() == null ? "" : r.actorUsername(), r.note() == null ? "" : r.note(), r.eventId(), r.hash()};
            for (int i = 0; i < values.length; i++) setCell(row.createCell(i), values[i]);
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNum - 1), 0, headers.length - 1));
    }

    private static void setCell(Cell cell, Object value) {
        if (value instanceof Number n) cell.setCellValue(n.doubleValue()); else cell.setCellValue(String.valueOf(value));
    }

    private AuditRow toRow(LocalStockMovement m) {
        return new AuditRow(m.getEventId(), m.getHospitalId(), m.getMedicineId(), medicineName(m.getMedicineId()), m.getQuantityBefore(), m.getDeltaQuantity(),
                m.getQuantityAfter(), m.getMovementType(), m.getReferenceType(), m.getReferenceId(), m.getActorUsername(), m.getNote(),
                m.getOccurredAt(), m.getHash(), m.getPreviousHash());
    }

    private AuditRow toRow(StockMovement m) {
        return new AuditRow(m.getEventId(), m.getHospitalId(), m.getMedicineId(), medicineName(m.getMedicineId()), m.getQuantityBefore(), m.getDeltaQuantity(),
                m.getQuantityAfter(), m.getMovementType(), m.getReferenceType(), m.getReferenceId(), m.getActorUsername(), m.getNote(),
                m.getOccurredAt(), m.getHash(), m.getPreviousHash());
    }

    public String medicineDisplayName(Long id) { return medicineName(id); }

    private String medicineName(Long id) {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        if (local != null) return local.findMedicine(id).map(Medicine::getName).orElse("Medicine #" + id);
        return medicines.findById(id).map(Medicine::getName).orElse("Medicine #" + id);
    }

    private List<Long> hospitalIds() {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        if (local != null) return local.hospitals().stream().map(h -> h.getHospitalId()).toList();
        try { return hospitals.findAll().stream().map(h -> h.getHospitalId()).toList(); } catch (RuntimeException ex) { return List.of(); }
    }

    private void syncLocalArchivesToCloud() {
        LocalAuditArchiveRepository local = localArchiveProvider.getIfAvailable();
        if (local == null) return;
        for (LocalAuditArchive meta : local.findAll()) {
            try {
                AuditArchive existing = cloudArchives.findByHospitalIdAndGranularityAndPeriodStart(meta.getHospitalId(), meta.getGranularity(), meta.getPeriodStart()).orElse(null);
                if (existing != null) continue;
                byte[] payload = Files.readAllBytes(Path.of(meta.getStorageName()));
                if (!sha256(payload).equals(meta.getSha256())) continue;
                AuditArchive a = new AuditArchive();
                a.setHospitalId(meta.getHospitalId()); a.setGranularity(meta.getGranularity()); a.setPeriodStart(meta.getPeriodStart());
                a.setPeriodEnd(meta.getPeriodEnd()); a.setStorageName(Path.of(meta.getStorageName()).getFileName().toString());
                a.setSha256(meta.getSha256()); a.setCreatedAt(meta.getCreatedAt()); a.setEncryptedPayload(payload);
                cloudArchives.saveAndFlush(a);
            } catch (RuntimeException | IOException ignored) { }
        }
    }

    private void compactDailyArchives(Long hospitalId, YearMonth month) {
        LocalAuditArchiveRepository local = localArchiveProvider.getIfAvailable();
        if (local != null) {
            for (LocalAuditArchive a : local.findByHospitalIdAndGranularityOrderByPeriodStartAsc(hospitalId, "DAILY")) {
                if (!a.getPeriodStart().isBefore(month.atDay(1)) && !a.getPeriodStart().isAfter(month.atEndOfMonth())) {
                    try { Files.deleteIfExists(Path.of(a.getStorageName())); } catch (IOException ignored) { }
                    local.delete(a);
                }
            }
        }
        try {
            for (AuditArchive a : cloudArchives.findByHospitalIdAndGranularityOrderByPeriodStartAsc(hospitalId, "DAILY")) {
                if (!a.getPeriodStart().isBefore(month.atDay(1)) && !a.getPeriodStart().isAfter(month.atEndOfMonth())) cloudArchives.delete(a);
            }
        } catch (RuntimeException ignored) { }
    }

    private void purgeOldHotRows(LocalDate cutoff) {
        LocalStockMovementRepository local = localLedgerProvider.getIfAvailable();
        if (local != null) { local.deleteByOccurredAtBeforeAndPendingSyncFalse(cutoff.atStartOfDay()); }
        try { cloudLedger.deleteByOccurredAtBefore(cutoff.atStartOfDay()); } catch (RuntimeException ignored) { }
    }

    private void purgeExpiredArchives(LocalDate cutoff) {
        LocalAuditArchiveRepository local = localArchiveProvider.getIfAvailable();
        if (local != null) {
            for (LocalAuditArchive a : local.findAll()) {
                if (a.getPeriodEnd().isBefore(cutoff)) {
                    try { Files.deleteIfExists(Path.of(a.getStorageName())); } catch (IOException ignored) { }
                    local.delete(a);
                }
            }
        }
        try {
            for (AuditArchive a : cloudArchives.findByPeriodEndBefore(cutoff)) cloudArchives.delete(a);
        } catch (RuntimeException ignored) { }
    }

    private SecretKeySpec secretKey() {
        if (key == null) throw new IllegalStateException("PULSE_AUDIT_ARCHIVE_KEY_BASE64 is not configured.");
        return new SecretKeySpec(key, "AES");
    }

    private static byte[] decodeKeyOrDerive(String configured, String fallbackSecret) {
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(configured.trim());
                if (decoded.length != 32) throw new IllegalArgumentException("Audit archive key must decode to 32 bytes.");
                return decoded;
            } catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid PULSE_AUDIT_ARCHIVE_KEY_BASE64.", ex); }
        }
        // Automatic fallback for the existing local/cloud setup: derive a stable 32-byte AES key
        // from the already-shared high-entropy LAN registration secret. An explicit audit key remains preferred.
        if (fallbackSecret != null && fallbackSecret.length() >= 32) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(("PULSE-AUDIT-" + fallbackSecret).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) { throw new IllegalStateException("Unable to derive the audit encryption key.", ex); }
        }
        return null;
    }

    private static String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static String key(String granularity, LocalDate start) { return granularity + "|" + start; }

    private record ArchiveResult(String fileName, byte[] payload, int entries) {}

    public record RequestAuditAttachment(String fileName, String sha256, LocalDate periodStart, LocalDate periodEnd, LocalDateTime createdAt, byte[] encryptedPayload) {}

    public record RequestEvidenceView(String medicineName, List<AuditRow> todayRows, List<AuditRow> monthRows) {}

    public record AuditRow(String eventId, Long hospitalId, Long medicineId, String medicineName,
                           Integer quantityBefore, int deltaQuantity, Integer quantityAfter,
                           String movementType, String referenceType, Long referenceId, String actorUsername, String note,
                           LocalDateTime occurredAt, String hash, String previousHash) { }
    public record ArchiveSummary(Long archiveId, String granularity, LocalDate periodStart, LocalDate periodEnd,
                                 String storageName, String sha256, String source) { }
}
