package com.pulse.service;

import com.pulse.local.model.LocalMedicineRequestActionLog;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.local.repository.LocalMedicineRequestActionLogRepository;
import com.pulse.model.MedicineRequestActionLog;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.MedicineRequestActionLogRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicineRequestActionLogService {
    private final MedicineRequestActionLogRepository cloud;
    private final ObjectProvider<LocalMedicineRequestActionLogRepository> localProvider;
    private final MedicineRequestSchemaService schema;
    private final HospitalRepository hospitals;
    private final MedicineRepository medicines;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public MedicineRequestActionLogService(MedicineRequestActionLogRepository cloud,
                                           ObjectProvider<LocalMedicineRequestActionLogRepository> localProvider,
                                           MedicineRequestSchemaService schema, HospitalRepository hospitals, MedicineRepository medicines,
                                           ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.cloud = cloud; this.localProvider = localProvider; this.schema = schema; this.hospitals = hospitals; this.medicines = medicines;
        this.localStoreProvider = localStoreProvider;
    }

    public synchronized MedicineRequestActionLog recordCloud(Long requestId, Long hospitalId, Long districtId, Long stateId,
                                                              Long medicineId, String actorRole, String actorUsername,
                                                              String tier, String action, String fromStatus, String toStatus,
                                                              int requestedQuantity, int fulfilledQuantity, String note,
                                                              LocalDateTime occurredAt) {
        return recordCloudWithEventId(null, requestId, hospitalId, districtId, stateId, medicineId, actorRole, actorUsername, tier,
                action, fromStatus, toStatus, requestedQuantity, fulfilledQuantity, note, occurredAt);
    }

    public synchronized MedicineRequestActionLog recordCloudWithEventId(String eventId, Long requestId, Long hospitalId, Long districtId, Long stateId,
                                                                         Long medicineId, String actorRole, String actorUsername,
                                                                         String tier, String action, String fromStatus, String toStatus,
                                                                         int requestedQuantity, int fulfilledQuantity, String note,
                                                                         LocalDateTime occurredAt) {
        schema.ensureTableOrThrow();
        if (eventId != null && cloud.findByEventId(eventId).isPresent()) return cloud.findByEventId(eventId).orElseThrow();
        var existing = cloud.findTopByOrderByOccurredAtDescActionLogIdDesc().orElse(null);
        MedicineRequestActionLog log = new MedicineRequestActionLog();
        if (eventId != null) log.setEventId(eventId);
        log.setRequestId(requestId); log.setHospitalId(hospitalId); log.setDistrictId(districtId); log.setStateId(stateId);
        log.setMedicineId(medicineId); log.setActorRole(actorRole); log.setActorUsername(actorUsername); log.setTier(tier);
        log.setAction(action); log.setFromStatus(fromStatus); log.setToStatus(toStatus); log.setRequestedQuantity(requestedQuantity);
        log.setFulfilledQuantity(fulfilledQuantity); log.setNote(note); log.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);
        log.setPreviousHash(existing == null ? null : existing.getHash());
        log.setHash(hash(log.getPreviousHash(), log.getEventId(), requestId, hospitalId, medicineId, actorUsername, tier, action,
                fromStatus, toStatus, requestedQuantity, fulfilledQuantity, note, log.getOccurredAt()));
        return cloud.saveAndFlush(log);
    }

    public LocalMedicineRequestActionLog recordLocal(Long requestId, Long localRequestId, Long hospitalId, Long districtId, Long stateId,
                                                     Long medicineId, String actorRole, String actorUsername, String tier,
                                                     String action, String fromStatus, String toStatus, int requestedQuantity,
                                                     int fulfilledQuantity, String note, LocalDateTime occurredAt) {
        var repo = localProvider.getIfAvailable();
        if (repo == null) return null;
        var existing = repo.findTopByOrderByOccurredAtDescActionLogIdDesc().orElse(null);
        LocalMedicineRequestActionLog log = new LocalMedicineRequestActionLog();
        log.setCloudRequestId(requestId); log.setLocalRequestId(localRequestId); log.setHospitalId(hospitalId); log.setDistrictId(districtId); log.setStateId(stateId);
        log.setMedicineId(medicineId); log.setActorRole(actorRole); log.setActorUsername(actorUsername); log.setTier(tier);
        log.setAction(action); log.setFromStatus(fromStatus); log.setToStatus(toStatus); log.setRequestedQuantity(requestedQuantity);
        log.setFulfilledQuantity(fulfilledQuantity); log.setNote(note); log.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);
        log.setPreviousHash(existing == null ? null : existing.getHash());
        log.setHash(hash(log.getPreviousHash(), log.getEventId(), requestId, hospitalId, medicineId, actorUsername, tier, action,
                fromStatus, toStatus, requestedQuantity, fulfilledQuantity, note, log.getOccurredAt()));
        return repo.saveAndFlush(log);
    }

    public void syncLocalRequestLogs(Long localRequestId, Long cloudRequestId) {
        var repo = localProvider.getIfAvailable(); if (repo == null) return;
        schema.ensureTableOrThrow();
        for (LocalMedicineRequestActionLog local : repo.findByLocalRequestIdOrderByOccurredAtAsc(localRequestId)) {
            local.setCloudRequestId(cloudRequestId);
            if (cloud.findByEventId(local.getEventId()).isPresent()) { local.setPendingSync(false); repo.save(local); continue; }
            MedicineRequestActionLog remote = new MedicineRequestActionLog();
            remote.setEventId(local.getEventId()); remote.setRequestId(cloudRequestId);
            remote.setHospitalId(local.getHospitalId()); remote.setDistrictId(local.getDistrictId()); remote.setStateId(local.getStateId());
            remote.setMedicineId(local.getMedicineId()); remote.setActorRole(local.getActorRole()); remote.setActorUsername(local.getActorUsername());
            remote.setTier(local.getTier()); remote.setAction(local.getAction()); remote.setFromStatus(local.getFromStatus()); remote.setToStatus(local.getToStatus());
            remote.setRequestedQuantity(local.getRequestedQuantity()); remote.setFulfilledQuantity(local.getFulfilledQuantity()); remote.setNote(local.getNote());
            remote.setOccurredAt(local.getOccurredAt());
            var previous = cloud.findTopByOrderByOccurredAtDescActionLogIdDesc().orElse(null);
            remote.setPreviousHash(previous == null ? null : previous.getHash());
            remote.setHash(hash(remote.getPreviousHash(), remote.getEventId(), remote.getRequestId(), remote.getHospitalId(), remote.getMedicineId(),
                    remote.getActorUsername(), remote.getTier(), remote.getAction(), remote.getFromStatus(), remote.getToStatus(),
                    remote.getRequestedQuantity(), remote.getFulfilledQuantity(), remote.getNote(), remote.getOccurredAt()));
            cloud.saveAndFlush(remote); local.setCloudRequestId(cloudRequestId); local.setPreviousHash(remote.getPreviousHash()); local.setHash(remote.getHash()); local.setPendingSync(false); repo.save(local);
        }
    }

    public List<MedicineRequestActionLog> cloudForHospital(Long hospitalId){ schema.ensureTableOrThrow(); return cloud.findByHospitalIdOrderByOccurredAtDesc(hospitalId); }
    public List<MedicineRequestActionLog> cloudForDistrict(Long districtId){ schema.ensureTableOrThrow(); return cloud.findByDistrictIdOrderByOccurredAtDesc(districtId); }
    public List<MedicineRequestActionLog> cloudForState(Long stateId){ schema.ensureTableOrThrow(); return cloud.findByStateIdOrderByOccurredAtDesc(stateId); }

    public List<LogView> hospitalViews(Long hospitalId) {
        var all = new java.util.ArrayList<MedicineRequestActionLog>();
        var local = localProvider.getIfAvailable();
        var localEventIds = new java.util.HashSet<String>();
        if (local != null) {
            for (LocalMedicineRequestActionLog l : local.findByHospitalIdOrderByOccurredAtDesc(hospitalId)) {
                MedicineRequestActionLog c = new MedicineRequestActionLog();
                c.setEventId(l.getEventId()); c.setRequestId(l.getCloudRequestId());
                c.setHospitalId(l.getHospitalId()); c.setDistrictId(l.getDistrictId()); c.setStateId(l.getStateId());
                c.setMedicineId(l.getMedicineId()); c.setActorRole(l.getActorRole()); c.setActorUsername(l.getActorUsername());
                c.setTier(l.getTier()); c.setAction(l.getAction()); c.setFromStatus(l.getFromStatus()); c.setToStatus(l.getToStatus());
                c.setRequestedQuantity(l.getRequestedQuantity()); c.setFulfilledQuantity(l.getFulfilledQuantity());
                c.setNote(l.getNote()); c.setOccurredAt(l.getOccurredAt()); c.setHash(l.getHash());
                all.add(c); localEventIds.add(c.getEventId());
            }
        }
        // If the local cache has records, it is enough for offline mode. When it is
        // empty, or when online, merge the cloud trail to expose synchronized history.
        if (all.isEmpty() || local == null) {
            try { schema.ensureTableOrThrow(); all.addAll(cloud.findByHospitalIdOrderByOccurredAtDesc(hospitalId)); }
            catch (RuntimeException ignored) { }
        } else {
            try {
                schema.ensureTableOrThrow();
                for (MedicineRequestActionLog c : cloud.findByHospitalIdOrderByOccurredAtDesc(hospitalId)) {
                    if (!localEventIds.contains(c.getEventId())) all.add(c);
                }
            } catch (RuntimeException ignored) { }
        }
        all.sort(java.util.Comparator.comparing(MedicineRequestActionLog::getOccurredAt).reversed());
        return views(all);
    }

    public List<LogView> districtViews(Long districtId) { schema.ensureTableOrThrow(); return views(cloud.findByDistrictIdOrderByOccurredAtDesc(districtId)); }
    public List<LogView> stateViews(Long stateId) { schema.ensureTableOrThrow(); return views(cloud.findByStateIdOrderByOccurredAtDesc(stateId)); }

    public record LogView(Long id, Long requestId, Long hospitalId, Long medicineId, String hospitalName, String medicineName,
                          String actorRole, String actorUsername, String tier, String action, String fromStatus, String toStatus,
                          int requestedQuantity, int fulfilledQuantity, String note, LocalDateTime occurredAt, String hash) {}

    private List<LogView> views(List<MedicineRequestActionLog> logs) {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        return logs.stream().map(l -> {
            String hospitalName = null;
            String medicineName = null;
            if (local != null && local.hasLocalData()) {
                hospitalName = local.findHospital(l.getHospitalId()).map(com.pulse.model.Hospital::getName).orElse(null);
                medicineName = local.findMedicine(l.getMedicineId()).map(com.pulse.model.Medicine::getName).orElse(null);
            }
            if (hospitalName == null) {
                try { hospitalName = hospitals.findById(l.getHospitalId()).map(com.pulse.model.Hospital::getName).orElse(null); }
                catch (RuntimeException ignored) { }
            }
            if (medicineName == null) {
                try { medicineName = medicines.findById(l.getMedicineId()).map(com.pulse.model.Medicine::getName).orElse(null); }
                catch (RuntimeException ignored) { }
            }
            return new LogView(l.getActionLogId(), l.getRequestId(), l.getHospitalId(), l.getMedicineId(),
                    hospitalName == null ? "Hospital #" + l.getHospitalId() : hospitalName,
                    medicineName == null ? "Medicine #" + l.getMedicineId() : medicineName,
                    l.getActorRole(), l.getActorUsername(), l.getTier(), l.getAction(), l.getFromStatus(), l.getToStatus(),
                    l.getRequestedQuantity(), l.getFulfilledQuantity(), l.getNote(), l.getOccurredAt(), l.getHash());
        }).toList();
    }

    private static String hash(String previous, Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = (previous == null ? "GENESIS" : previous) + "|" + java.util.Arrays.stream(values).map(v -> v == null ? "" : String.valueOf(v)).reduce((a,b)->a+"|"+b).orElse("");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Unable to hash request audit event.", e); }
    }
}
