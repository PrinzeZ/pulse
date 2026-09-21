package com.pulse.service;

import com.pulse.local.model.LocalStockMovement;
import com.pulse.local.repository.LocalStockMovementRepository;
import com.pulse.model.StockMovement;
import com.pulse.repository.StockMovementRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class StockLedgerService {

    private final ObjectProvider<LocalStockMovementRepository> localProvider;
    private final StockMovementRepository cloudRepository;
    private final SupplyChainSchemaService schema;

    public StockLedgerService(ObjectProvider<LocalStockMovementRepository> localProvider,
                              StockMovementRepository cloudRepository,
                              SupplyChainSchemaService schema) {
        this.localProvider = localProvider;
        this.cloudRepository = cloudRepository;
        this.schema = schema;
    }

    public LocalStockMovement recordLocal(Long hospitalId, Long medicineId, int delta,
                                          String type, String referenceType, Long referenceId,
                                          String actor, String note) {
        LocalStockMovementRepository repo = localProvider.getIfAvailable();
        if (repo == null) throw new IllegalStateException("Local ledger is not enabled.");

        LocalStockMovement previous = latestLocal(repo, hospitalId);
        LocalStockMovement movement = new LocalStockMovement();
        movement.setEventId(UUID.randomUUID().toString());
        movement.setHospitalId(hospitalId);
        movement.setMedicineId(medicineId);
        movement.setDeltaQuantity(delta);
        movement.setMovementType(type);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setActorUsername(actor);
        movement.setNote(note);
        movement.setOccurredAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        movement.setPreviousHash(previous == null ? null : previous.getHash());
        movement.setHash(hash(
                movement.getEventId(), movement.getHospitalId(), movement.getMedicineId(),
                movement.getDeltaQuantity(), movement.getMovementType(), movement.getReferenceType(),
                movement.getReferenceId(), movement.getActorUsername(), movement.getNote(),
                movement.getOccurredAt(), movement.getPreviousHash()));
        movement.setPendingSync(true);
        return repo.saveAndFlush(movement);
    }

    public StockMovement recordCloud(Long hospitalId, Long medicineId, int delta,
                                     String type, String referenceType, Long referenceId,
                                     String actor, String note) {
        if (!schema.ensureTables()) throw new IllegalStateException("Cloud ledger is unavailable.");

        StockMovement previous = latestCloud(hospitalId);
        StockMovement movement = new StockMovement();
        movement.setEventId(UUID.randomUUID().toString());
        movement.setHospitalId(hospitalId);
        movement.setMedicineId(medicineId);
        movement.setDeltaQuantity(delta);
        movement.setMovementType(type);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setActorUsername(actor);
        movement.setNote(note);
        movement.setOccurredAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        movement.setPreviousHash(previous == null ? null : previous.getHash());
        movement.setHash(hash(
                movement.getEventId(), movement.getHospitalId(), movement.getMedicineId(),
                movement.getDeltaQuantity(), movement.getMovementType(), movement.getReferenceType(),
                movement.getReferenceId(), movement.getActorUsername(), movement.getNote(),
                movement.getOccurredAt(), movement.getPreviousHash()));
        return cloudRepository.saveAndFlush(movement);
    }

    public Verification verifyLocal(Long hospitalId) {
        LocalStockMovementRepository repo = localProvider.getIfAvailable();
        if (repo == null) return new Verification(false, 0, "LOCAL_LEDGER_DISABLED");
        String previousHash = null;
        int count = 0;
        for (LocalStockMovement m : repo.findByHospitalIdOrderByOccurredAtAscLocalMovementIdAsc(hospitalId)) {
            String expected = hash(
                    m.getEventId(), m.getHospitalId(), m.getMedicineId(), m.getDeltaQuantity(),
                    m.getMovementType(), m.getReferenceType(), m.getReferenceId(),
                    m.getActorUsername(), m.getNote(), m.getOccurredAt(), m.getPreviousHash());
            if (!equals(previousHash, m.getPreviousHash()) || !expected.equals(m.getHash())) {
                return new Verification(false, count, "HASH_CHAIN_MISMATCH at " + m.getEventId());
            }
            previousHash = m.getHash();
            count++;
        }
        return new Verification(true, count, "LOCAL_LEDGER_VALID");
    }

    public Verification verifyCloud(Long hospitalId) {
        if (!schema.ensureTables()) return new Verification(false, 0, "CLOUD_LEDGER_UNAVAILABLE");
        String previousHash = null;
        int count = 0;
        for (StockMovement m : cloudRepository.findByHospitalIdOrderByOccurredAtAscMovementIdAsc(hospitalId)) {
            String expected = hash(
                    m.getEventId(), m.getHospitalId(), m.getMedicineId(), m.getDeltaQuantity(),
                    m.getMovementType(), m.getReferenceType(), m.getReferenceId(),
                    m.getActorUsername(), m.getNote(), m.getOccurredAt(), m.getPreviousHash());
            if (!equals(previousHash, m.getPreviousHash()) || !expected.equals(m.getHash())) {
                return new Verification(false, count, "HASH_CHAIN_MISMATCH at " + m.getEventId());
            }
            previousHash = m.getHash();
            count++;
        }
        return new Verification(true, count, "CLOUD_LEDGER_VALID");
    }

    public List<LocalStockMovement> localMovements(Long hospitalId) {
        LocalStockMovementRepository repo = localProvider.getIfAvailable();
        return repo == null ? List.of() : repo.findByHospitalIdOrderByOccurredAtAscLocalMovementIdAsc(hospitalId);
    }

    public record Verification(boolean valid, int entries, String message) {}

    private LocalStockMovement latestLocal(LocalStockMovementRepository repo, Long hospitalId) {
        List<LocalStockMovement> all = repo.findByHospitalIdOrderByOccurredAtAscLocalMovementIdAsc(hospitalId);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    private StockMovement latestCloud(Long hospitalId) {
        List<StockMovement> all = cloudRepository.findByHospitalIdOrderByOccurredAtAscMovementIdAsc(hospitalId);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = java.util.Arrays.stream(values)
                    .map(v -> {
                        if (v == null) return "<null>";
                        if (v instanceof LocalDateTime time) {
                            return time.truncatedTo(ChronoUnit.MICROS).toString();
                        }
                        return v.toString();
                    })
                    .reduce((a, b) -> a + "|" + b)
                    .orElse("");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate ledger hash.", ex);
        }
    }
}
