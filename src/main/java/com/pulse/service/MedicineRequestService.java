package com.pulse.service;

import com.pulse.local.model.LocalMedicineRequest;
import com.pulse.local.repository.LocalMedicineRequestRepository;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Hospital;
import com.pulse.model.Medicine;
import com.pulse.model.MedicineRequest;
import com.pulse.model.MedicineRequestStatus;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.MedicineRequestRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicineRequestService {

    private final MedicineRequestRepository cloudRequests;
    private final HospitalRepository cloudHospitals;
    private final MedicineRepository cloudMedicines;
    private final ObjectProvider<LocalMedicineRequestRepository> localRequestsProvider;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final MedicineRequestSchemaService schema;

    public MedicineRequestService(MedicineRequestRepository cloudRequests,
                                  HospitalRepository cloudHospitals,
                                  MedicineRepository cloudMedicines,
                                  ObjectProvider<LocalMedicineRequestRepository> localRequestsProvider,
                                  ObjectProvider<LocalOfflineStore> localStoreProvider,
                                  MedicineRequestSchemaService schema) {
        this.cloudRequests = cloudRequests;
        this.cloudHospitals = cloudHospitals;
        this.cloudMedicines = cloudMedicines;
        this.localRequestsProvider = localRequestsProvider;
        this.localStoreProvider = localStoreProvider;
        this.schema = schema;
    }

    public CreateResult createHospitalRequest(Long hospitalId, Long districtId, Long stateId,
                                              Long medicineId, int quantity, String note,
                                              String requestedByUsername) {
        if (quantity <= 0) throw new IllegalArgumentException("Requested quantity must be greater than zero.");

        LocalMedicineRequestRepository local = localRequestsProvider.getIfAvailable();
        LocalOfflineStore localStore = localStoreProvider.getIfAvailable();

        Medicine medicine = localStore != null
                ? localStore.findMedicine(medicineId).orElse(null)
                : cloudMedicines.findById(medicineId).orElse(null);
        if (medicine == null) throw new IllegalArgumentException("Medicine is not available in this hospital's data.");

        if (districtId == null) {
            Hospital hospital = localStore != null
                    ? localStore.findHospital(hospitalId).orElse(null)
                    : cloudHospitals.findById(hospitalId).orElse(null);
            if (hospital != null) districtId = hospital.getDistrictId();
        }
        if (districtId == null) throw new IllegalArgumentException("Hospital district is not configured.");

        LocalMedicineRequest localRequest = null;
        if (local != null) {
            localRequest = new LocalMedicineRequest();
            localRequest.setHospitalId(hospitalId);
            localRequest.setDistrictId(districtId);
            localRequest.setStateId(stateId);
            localRequest.setMedicineId(medicineId);
            localRequest.setRequestedQuantity(quantity);
            localRequest.setFulfilledQuantity(0);
            localRequest.setStatus(MedicineRequestStatus.PENDING_DISTRICT.name());
            localRequest.setRequestedByUsername(requestedByUsername);
            localRequest.setLastUpdatedByUsername(requestedByUsername);
            localRequest.setDistrictNote(note);
            localRequest.setCreatedAt(LocalDateTime.now());
            localRequest.setUpdatedAt(LocalDateTime.now());
            localRequest.setPendingSync(true);
            localRequest = local.saveAndFlush(localRequest);
        }

        try {
            if (!schema.ensureTable()) throw new IllegalStateException("Cloud is unavailable.");
            MedicineRequest cloud = new MedicineRequest();
            cloud.setHospitalId(hospitalId);
            cloud.setDistrictId(districtId);
            cloud.setStateId(stateId);
            cloud.setMedicineId(medicineId);
            cloud.setRequestedQuantity(quantity);
            cloud.setFulfilledQuantity(0);
            cloud.setStatus(MedicineRequestStatus.PENDING_DISTRICT);
            cloud.setRequestedByUsername(requestedByUsername);
            cloud.setLastUpdatedByUsername(requestedByUsername);
            cloud.setDistrictNote(note);
            cloud.setCreatedAt(LocalDateTime.now());
            cloud.setUpdatedAt(LocalDateTime.now());
            cloud = cloudRequests.saveAndFlush(cloud);

            if (localRequest != null) {
                localRequest.setCloudRequestId(cloud.getRequestId());
                localRequest.setPendingSync(false);
                localRequest.setUpdatedAt(cloud.getUpdatedAt());
                local.save(localRequest);
            }
            return new CreateResult(cloud.getRequestId(), false, "Request submitted to district.");
        } catch (RuntimeException ex) {
            if (localRequest != null) {
                return new CreateResult(localRequest.getLocalRequestId(), true,
                        "Request saved locally. It will be submitted when the connection is restored.");
            }
            throw new IllegalStateException("Unable to submit the request.", ex);
        }
    }

    public List<LocalMedicineRequest> localHospitalRequests(Long hospitalId) {
        LocalMedicineRequestRepository local = localRequestsProvider.getIfAvailable();
        return local == null ? List.of() : local.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
    }

    public List<MedicineRequest> cloudHospitalRequests(Long hospitalId) {
        ensureCloud();
        return cloudRequests.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
    }

    public List<MedicineRequest> districtRequests(Long districtId) {
        ensureCloud();
        return cloudRequests.findByDistrictIdAndStatusInOrderByCreatedAtDesc(districtId, List.of(
                MedicineRequestStatus.PENDING_DISTRICT,
                MedicineRequestStatus.UNDER_REVIEW,
                MedicineRequestStatus.APPROVED,
                MedicineRequestStatus.PARTIALLY_FULFILLED,
                MedicineRequestStatus.ESCALATED_TO_STATE
        ));
    }

    public List<MedicineRequest> stateRequests(Long stateId) {
        ensureCloud();
        return cloudRequests.findByStateIdAndStatusInOrderByCreatedAtDesc(stateId, List.of(
                MedicineRequestStatus.ESCALATED_TO_STATE,
                MedicineRequestStatus.STATE_APPROVED,
                MedicineRequestStatus.STATE_PARTIALLY_FULFILLED,
                MedicineRequestStatus.REJECTED,
                MedicineRequestStatus.FULFILLED
        ));
    }

    public MedicineRequest districtAction(Long districtId, Long requestId, String action,
                                          Integer fulfilledQuantity, String note, String actor) {
        ensureCloud();
        MedicineRequest request = cloudRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (!districtId.equals(request.getDistrictId())) throw new IllegalArgumentException("Request is outside your district.");
        if (request.getStatus() == MedicineRequestStatus.REJECTED ||
                request.getStatus() == MedicineRequestStatus.FULFILLED) {
            throw new IllegalArgumentException("This request is already closed.");
        }

        String normalized = normalizeAction(action);
        switch (normalized) {
            case "REVIEW" -> request.setStatus(MedicineRequestStatus.UNDER_REVIEW);
            case "APPROVE" -> {
                request.setStatus(MedicineRequestStatus.APPROVED);
                request.setFulfilledQuantity(0);
            }
            case "PARTIAL" -> {
                int qty = validateFulfilled(fulfilledQuantity, request.getRequestedQuantity());
                request.setFulfilledQuantity(qty);
                request.setStatus(qty >= request.getRequestedQuantity()
                        ? MedicineRequestStatus.FULFILLED
                        : MedicineRequestStatus.PARTIALLY_FULFILLED);
            }
            case "FULFILL" -> {
                request.setFulfilledQuantity(request.getRequestedQuantity());
                request.setStatus(MedicineRequestStatus.FULFILLED);
            }
            case "REJECT" -> request.setStatus(MedicineRequestStatus.REJECTED);
            case "ESCALATE" -> request.setStatus(MedicineRequestStatus.ESCALATED_TO_STATE);
            default -> throw new IllegalArgumentException("Unsupported district action.");
        }

        request.setDistrictNote(blankToNull(note));
        request.setLastUpdatedByUsername(actor);
        request.setUpdatedAt(LocalDateTime.now());
        return cloudRequests.saveAndFlush(request);
    }

    public MedicineRequest stateAction(Long stateId, Long requestId, String action,
                                       Integer fulfilledQuantity, String note, String actor) {
        ensureCloud();
        MedicineRequest request = cloudRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (request.getStateId() != null && !stateId.equals(request.getStateId())) {
            throw new IllegalArgumentException("Request is outside your state scope.");
        }
        if (request.getStatus() != MedicineRequestStatus.ESCALATED_TO_STATE &&
                request.getStatus() != MedicineRequestStatus.STATE_APPROVED &&
                request.getStatus() != MedicineRequestStatus.STATE_PARTIALLY_FULFILLED) {
            throw new IllegalArgumentException("This request is not awaiting state action.");
        }

        String normalized = normalizeAction(action);
        switch (normalized) {
            case "REVIEW", "APPROVE" -> request.setStatus(MedicineRequestStatus.STATE_APPROVED);
            case "PARTIAL" -> {
                int qty = validateFulfilled(fulfilledQuantity, request.getRequestedQuantity());
                request.setFulfilledQuantity(qty);
                request.setStatus(qty >= request.getRequestedQuantity()
                        ? MedicineRequestStatus.FULFILLED
                        : MedicineRequestStatus.STATE_PARTIALLY_FULFILLED);
            }
            case "FULFILL" -> {
                request.setFulfilledQuantity(request.getRequestedQuantity());
                request.setStatus(MedicineRequestStatus.FULFILLED);
            }
            case "REJECT" -> request.setStatus(MedicineRequestStatus.REJECTED);
            default -> throw new IllegalArgumentException("Unsupported state action.");
        }

        request.setStateNote(blankToNull(note));
        request.setLastUpdatedByUsername(actor);
        request.setUpdatedAt(LocalDateTime.now());
        return cloudRequests.saveAndFlush(request);
    }

    /**
     * Pushes hospital-created local requests to the cloud and then refreshes
     * their status. This is intentionally separate from stock synchronization.
     */
    public SyncResult syncHospital(Long hospitalId) {
        LocalMedicineRequestRepository local = localRequestsProvider.getIfAvailable();
        if (local == null) return new SyncResult(0, 0, "LOCAL_SYNC_DISABLED");

        int pushed = 0;
        int pulled = 0;
        try {
            ensureCloud();
            for (LocalMedicineRequest item : local.findByHospitalIdAndPendingSyncTrueOrderByCreatedAtAsc(hospitalId)) {
                MedicineRequest cloud;
                if (item.getCloudRequestId() == null) {
                    cloud = new MedicineRequest();
                } else {
                    cloud = cloudRequests.findById(item.getCloudRequestId()).orElseGet(MedicineRequest::new);
                }
                boolean existingCloud = cloud.getRequestId() != null;
                copyToCloud(item, cloud);
                if (!existingCloud) cloud.setRequestId(null);
                cloud = cloudRequests.saveAndFlush(cloud);
                copyFromCloud(cloud, item);
                item.setPendingSync(false);
                local.save(item);
                pushed++;
            }

            for (MedicineRequest cloud : cloudRequests.findByHospitalIdOrderByCreatedAtDesc(hospitalId)) {
                LocalMedicineRequest item = local.findByCloudRequestId(cloud.getRequestId()).orElseGet(() -> new LocalMedicineRequest());
                if (item.isPendingSync()) continue;
                copyFromCloud(cloud, item);
                item.setPendingSync(false);
                local.save(item);
                pulled++;
            }
            return new SyncResult(pushed, pulled, "SYNCED");
        } catch (RuntimeException ex) {
            long pending = local.findByHospitalIdAndPendingSyncTrueOrderByCreatedAtAsc(hospitalId).size();
            return new SyncResult((int) pending, 0, "OFFLINE: " + ex.getClass().getSimpleName());
        }
    }

    private void copyToCloud(LocalMedicineRequest local, MedicineRequest cloud) {
        cloud.setRequestId(local.getCloudRequestId());
        cloud.setHospitalId(local.getHospitalId());
        cloud.setDistrictId(local.getDistrictId());
        cloud.setStateId(local.getStateId());
        cloud.setMedicineId(local.getMedicineId());
        cloud.setRequestedQuantity(local.getRequestedQuantity());
        cloud.setFulfilledQuantity(local.getFulfilledQuantity());
        cloud.setStatus(MedicineRequestStatus.valueOf(local.getStatus()));
        cloud.setRequestedByUsername(local.getRequestedByUsername());
        cloud.setLastUpdatedByUsername(local.getLastUpdatedByUsername());
        cloud.setDistrictNote(local.getDistrictNote());
        cloud.setStateNote(local.getStateNote());
        cloud.setCreatedAt(local.getCreatedAt());
        cloud.setUpdatedAt(local.getUpdatedAt());
    }

    private void copyFromCloud(MedicineRequest cloud, LocalMedicineRequest local) {
        local.setCloudRequestId(cloud.getRequestId());
        local.setHospitalId(cloud.getHospitalId());
        local.setDistrictId(cloud.getDistrictId());
        local.setStateId(cloud.getStateId());
        local.setMedicineId(cloud.getMedicineId());
        local.setRequestedQuantity(cloud.getRequestedQuantity());
        local.setFulfilledQuantity(cloud.getFulfilledQuantity());
        local.setStatus(cloud.getStatus().name());
        local.setRequestedByUsername(cloud.getRequestedByUsername());
        local.setLastUpdatedByUsername(cloud.getLastUpdatedByUsername());
        local.setDistrictNote(cloud.getDistrictNote());
        local.setStateNote(cloud.getStateNote());
        local.setCreatedAt(cloud.getCreatedAt());
        local.setUpdatedAt(cloud.getUpdatedAt());
    }

    private void ensureCloud() {
        if (!schema.ensureTable()) throw new IllegalStateException("Cloud is unavailable.");
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim().toUpperCase();
    }

    private static int validateFulfilled(Integer quantity, int requested) {
        if (quantity == null || quantity < 0 || quantity > requested) {
            throw new IllegalArgumentException("Fulfilled quantity must be between 0 and the requested quantity.");
        }
        return quantity;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


    public List<Medicine> availableMedicines() {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        if (local != null) return local.medicines();
        return cloudMedicines.findAll().stream()
                .sorted(java.util.Comparator.comparing(Medicine::getName))
                .toList();
    }

    public List<RequestView> hospitalViews(Long hospitalId) {
        LocalMedicineRequestRepository local = localRequestsProvider.getIfAvailable();
        if (local != null) {
            return local.findByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                    .map(this::view)
                    .toList();
        }
        return cloudHospitalRequests(hospitalId).stream().map(this::view).toList();
    }

    public List<RequestView> districtViews(Long districtId) {
        return districtRequests(districtId).stream().map(this::view).toList();
    }

    public List<RequestView> stateViews(Long stateId) {
        return stateRequests(stateId).stream().map(this::view).toList();
    }

    private RequestView view(MedicineRequest request) {
        Medicine medicine = cloudMedicines.findById(request.getMedicineId()).orElse(null);
        Hospital hospital = cloudHospitals.findById(request.getHospitalId()).orElse(null);
        return new RequestView(request.getRequestId(), hospital == null ? "Hospital #" + request.getHospitalId() : hospital.getName(),
                medicine == null ? "Medicine #" + request.getMedicineId() : medicine.getName(),
                request.getStatus().name(), request.getRequestedQuantity(), request.getFulfilledQuantity(),
                request.getDistrictNote() != null ? request.getDistrictNote() : request.getStateNote(),
                request.getRequestedByUsername(), request.getCreatedAt(), false);
    }

    private RequestView view(LocalMedicineRequest request) {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        Medicine medicine = local == null ? null : local.findMedicine(request.getMedicineId()).orElse(null);
        Hospital hospital = local == null ? null : local.findHospital(request.getHospitalId()).orElse(null);
        return new RequestView(request.getLocalRequestId(),
                hospital == null ? "Hospital #" + request.getHospitalId() : hospital.getName(),
                medicine == null ? "Medicine #" + request.getMedicineId() : medicine.getName(),
                request.getStatus(), request.getRequestedQuantity(), request.getFulfilledQuantity(),
                request.getDistrictNote() != null ? request.getDistrictNote() : request.getStateNote(),
                request.getRequestedByUsername(), request.getCreatedAt(), request.isPendingSync());
    }

    public record CreateResult(Long id, boolean localPending, String message) {}
    public record SyncResult(int pushed, int pulled, String status) {}
    public record RequestView(Long id, String hospitalName, String medicineName, String status,
                              int requestedQuantity, int fulfilledQuantity, String note,
                              String requestedBy, LocalDateTime createdAt, boolean localPending) {}
}
