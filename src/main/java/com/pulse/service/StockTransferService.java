package com.pulse.service;

import com.pulse.local.model.LocalMedicineRequest;
import com.pulse.local.model.LocalStockEntry;
import com.pulse.local.model.LocalStockTransfer;
import com.pulse.local.repository.LocalMedicineRequestRepository;
import com.pulse.local.repository.LocalStockEntryRepository;
import com.pulse.local.repository.LocalStockTransferRepository;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Hospital;
import com.pulse.model.MedicineRequest;
import com.pulse.model.MedicineRequestStatus;
import com.pulse.model.StockEntry;
import com.pulse.model.StockTransfer;
import com.pulse.model.StockTransferStatus;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRequestRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.repository.StockTransferRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class StockTransferService {

    private final StockTransferRepository cloudTransfers;
    private final StockEntryRepository cloudStock;
    private final MedicineRequestRepository cloudRequests;
    private final HospitalRepository cloudHospitals;
    private final ObjectProvider<LocalStockTransferRepository> localTransfersProvider;
    private final ObjectProvider<LocalStockEntryRepository> localStockProvider;
    private final ObjectProvider<LocalMedicineRequestRepository> localRequestsProvider;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final SupplyChainSchemaService schema;
    private final StockLedgerService ledger;

    public StockTransferService(StockTransferRepository cloudTransfers,
                                StockEntryRepository cloudStock,
                                MedicineRequestRepository cloudRequests,
                                HospitalRepository cloudHospitals,
                                ObjectProvider<LocalStockTransferRepository> localTransfersProvider,
                                ObjectProvider<LocalStockEntryRepository> localStockProvider,
                                ObjectProvider<LocalMedicineRequestRepository> localRequestsProvider,
                                ObjectProvider<LocalOfflineStore> localStoreProvider,
                                SupplyChainSchemaService schema,
                                StockLedgerService ledger) {
        this.cloudTransfers = cloudTransfers;
        this.cloudStock = cloudStock;
        this.cloudRequests = cloudRequests;
        this.cloudHospitals = cloudHospitals;
        this.localTransfersProvider = localTransfersProvider;
        this.localStockProvider = localStockProvider;
        this.localRequestsProvider = localRequestsProvider;
        this.localStoreProvider = localStoreProvider;
        this.schema = schema;
        this.ledger = ledger;
    }

    public CreateResult createTransfer(Long requestId, Long sourceHospitalId, int quantity,
                                       String actor, String note) {
        if (quantity <= 0) throw new IllegalArgumentException("Transfer quantity must be greater than zero.");
        if (sourceHospitalId == null) throw new IllegalArgumentException("A source hospital is required.");

        MedicineRequest request = findCloudRequest(requestId);
        if (request == null) throw new IllegalArgumentException("The request must be synchronized before a transfer is created.");

        if (!isTransferable(request.getStatus())) {
            throw new IllegalArgumentException("The request must be approved before stock can be transferred.");
        }
        if (sourceHospitalId.equals(request.getHospitalId())) {
            throw new IllegalArgumentException("Source and destination hospitals must be different.");
        }

        int alreadyAllocated = cloudTransfers.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
                .filter(t -> t.getStatus() != StockTransferStatus.CANCELLED && t.getStatus() != StockTransferStatus.REJECTED)
                .mapToInt(StockTransfer::getQuantity)
                .sum();
        int remaining = request.getRequestedQuantity() - alreadyAllocated;
        if (quantity > remaining) {
            throw new IllegalArgumentException("Transfer quantity exceeds the remaining request quantity (" + remaining + ").");
        }

        LocalStockTransferRepository local = localTransfersProvider.getIfAvailable();
        LocalStockTransfer localTransfer = null;
        if (local != null) {
            localTransfer = new LocalStockTransfer();
            copyToLocal(newTransfer(request, sourceHospitalId, quantity, actor, note), localTransfer);
            localTransfer.setPendingSync(true);
            localTransfer = local.saveAndFlush(localTransfer);
        }

        try {
            if (!schema.ensureTables()) throw new IllegalStateException("Cloud is unavailable.");
            StockTransfer cloud = newTransfer(request, sourceHospitalId, quantity, actor, note);
            cloud = cloudTransfers.saveAndFlush(cloud);
            if (localTransfer != null) {
                copyFromCloud(cloud, localTransfer);
                localTransfer.setPendingSync(false);
                local.saveAndFlush(localTransfer);
            }
            return new CreateResult(cloud.getTransferId(), false,
                    "Transfer created. The source hospital can now dispatch the stock.");
        } catch (RuntimeException ex) {
            if (localTransfer != null) {
                return new CreateResult(localTransfer.getLocalTransferId(), true,
                        "Transfer saved locally and queued for cloud synchronization.");
            }
            throw new IllegalStateException("Unable to create the stock transfer.", ex);
        }
    }

    public List<TransferView> districtViews(Long districtId) {
        try {
            if (!schema.ensureTables()) return List.of();
            return cloudTransfers.findAll().stream()
                    .filter(t -> hospitalInDistrict(t.getDestinationHospitalId(), districtId)
                            || hospitalInDistrict(t.getSourceHospitalId(), districtId))
                    .sorted(Comparator.comparing(StockTransfer::getCreatedAt).reversed())
                    .map(this::view)
                    .toList();
        } catch (RuntimeException ex) {
            return localViewsForScope(districtId);
        }
    }

    public List<TransferView> hospitalViews(Long hospitalId) {
        LocalStockTransferRepository local = localTransfersProvider.getIfAvailable();
        if (local != null) {
            return local.findBySourceHospitalIdOrDestinationHospitalIdOrderByCreatedAtDesc(hospitalId, hospitalId)
                    .stream().map(this::view).toList();
        }
        try {
            if (!schema.ensureTables()) return List.of();
            return cloudTransfers.findBySourceHospitalIdOrDestinationHospitalIdOrderByCreatedAtDesc(hospitalId, hospitalId)
                    .stream().map(this::view).toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    public void dispatch(Long transferId, Long hospitalId, String actor) {
        LocalStockTransferRepository localTransfers = localTransfersProvider.getIfAvailable();
        LocalStockEntryRepository localStock = localStockProvider.getIfAvailable();

        if (localTransfers != null && localStock != null) {
            LocalStockTransfer transfer = resolveLocalTransfer(localTransfers, transferId);
            if (transfer == null) throw new IllegalArgumentException("Transfer is not available on this hospital node.");
            authorizeSource(transfer.getSourceHospitalId(), hospitalId);
            StockTransferStatus status = parseStatus(transfer.getStatus());
            if (status == StockTransferStatus.DISPATCHED || status == StockTransferStatus.IN_TRANSIT) {
                return;
            }
            if (status == StockTransferStatus.RECEIVED || status == StockTransferStatus.CANCELLED || status == StockTransferStatus.REJECTED) {
                throw new IllegalArgumentException("This transfer can no longer be dispatched.");
            }

            LocalStockEntry stock = localStock.findByHospitalIdAndMedicineId(hospitalId, transfer.getMedicineId())
                    .orElseThrow(() -> new IllegalArgumentException("Source medicine stock is not available on this node."));
            if (stock.getQuantity() < transfer.getQuantity()) {
                throw new IllegalArgumentException("Insufficient source stock. Available: " + stock.getQuantity());
            }

            int previousQuantity = stock.getQuantity();
            int nextQuantity = previousQuantity - transfer.getQuantity();
            stock.setQuantity(nextQuantity);
            stock.setLastUpdated(java.time.LocalDate.now().toString());
            stock.setSynced(false);
            localStock.saveAndFlush(stock);

            ledger.recordLocal(hospitalId, transfer.getMedicineId(), -transfer.getQuantity(),
                    "TRANSFER_OUT", "STOCK_TRANSFER", transfer.getCloudTransferId() != null
                            ? transfer.getCloudTransferId() : transfer.getLocalTransferId(), actor,
                    "Dispatched medicine transfer.", previousQuantity, nextQuantity);

            transfer.setStatus(StockTransferStatus.IN_TRANSIT.name());
            transfer.setDispatchedByUsername(actor);
            transfer.setUpdatedAt(LocalDateTime.now());
            transfer.setPendingSync(true);
            localTransfers.saveAndFlush(transfer);
            tryCloudUpdate(transfer);
            return;
        }

        StockTransfer transfer = cloudTransfer(transferId);
        authorizeSource(transfer.getSourceHospitalId(), hospitalId);
        if (transfer.getStatus() == StockTransferStatus.RECEIVED) throw new IllegalArgumentException("Transfer already received.");
        StockEntry stock = cloudStock.findByHospitalIdAndMedId(hospitalId, transfer.getMedicineId())
                .orElseThrow(() -> new IllegalArgumentException("Source stock is not available."));
        if (stock.getQuantity() < transfer.getQuantity()) throw new IllegalArgumentException("Insufficient source stock.");
        int previousQuantity = stock.getQuantity();
        int nextQuantity = previousQuantity - transfer.getQuantity();
        stock.setQuantity(nextQuantity);
        stock.setLastUpdated(java.time.LocalDate.now());
        cloudStock.saveAndFlush(stock);
        ledger.recordCloud(hospitalId, transfer.getMedicineId(), -transfer.getQuantity(),
                "TRANSFER_OUT", "STOCK_TRANSFER", transfer.getTransferId(), actor, "Dispatched medicine transfer.", previousQuantity, nextQuantity);
        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        transfer.setDispatchedByUsername(actor);
        cloudTransfers.saveAndFlush(transfer);
    }

    public void receive(Long transferId, Long hospitalId, String actor) {
        LocalStockTransferRepository localTransfers = localTransfersProvider.getIfAvailable();
        LocalStockEntryRepository localStock = localStockProvider.getIfAvailable();

        if (localTransfers != null && localStock != null) {
            LocalStockTransfer transfer = resolveLocalTransfer(localTransfers, transferId);
            if (transfer == null) throw new IllegalArgumentException("Transfer is not available on this hospital node.");
            authorizeDestination(transfer.getDestinationHospitalId(), hospitalId);
            StockTransferStatus status = parseStatus(transfer.getStatus());
            if (status == StockTransferStatus.RECEIVED) return;
            if (status != StockTransferStatus.IN_TRANSIT) {
                throw new IllegalArgumentException("Only an in-transit transfer can be received.");
            }

            LocalStockEntry stock = localStock.findByHospitalIdAndMedicineId(hospitalId, transfer.getMedicineId())
                    .orElseGet(LocalStockEntry::new);
            stock.setHospitalId(hospitalId);
            stock.setMedicineId(transfer.getMedicineId());
            int previousQuantity = stock.getQuantity();
            int nextQuantity = previousQuantity + transfer.getQuantity();
            stock.setQuantity(nextQuantity);
            stock.setLastUpdated(java.time.LocalDate.now().toString());
            stock.setSynced(false);
            localStock.saveAndFlush(stock);

            ledger.recordLocal(hospitalId, transfer.getMedicineId(), transfer.getQuantity(),
                    "TRANSFER_IN", "STOCK_TRANSFER", transfer.getCloudTransferId() != null
                            ? transfer.getCloudTransferId() : transfer.getLocalTransferId(), actor,
                    "Received medicine transfer.", previousQuantity, nextQuantity);

            transfer.setStatus(StockTransferStatus.RECEIVED.name());
            transfer.setReceivedByUsername(actor);
            transfer.setUpdatedAt(LocalDateTime.now());
            transfer.setPendingSync(true);
            localTransfers.saveAndFlush(transfer);
            tryCloudUpdate(transfer);
            refreshRequestFulfillment(transfer.getRequestId());
            return;
        }

        StockTransfer transfer = cloudTransfer(transferId);
        authorizeDestination(transfer.getDestinationHospitalId(), hospitalId);
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw new IllegalArgumentException("Only an in-transit transfer can be received.");
        }
        StockEntry stock = cloudStock.findByHospitalIdAndMedId(hospitalId, transfer.getMedicineId())
                .orElseGet(() -> new StockEntry(null, hospitalId, transfer.getMedicineId(), 0));
        int previousQuantity = stock.getQuantity();
        int nextQuantity = previousQuantity + transfer.getQuantity();
        stock.setQuantity(nextQuantity);
        stock.setLastUpdated(java.time.LocalDate.now());
        cloudStock.saveAndFlush(stock);
        ledger.recordCloud(hospitalId, transfer.getMedicineId(), transfer.getQuantity(),
                "TRANSFER_IN", "STOCK_TRANSFER", transfer.getTransferId(), actor, "Received medicine transfer.", previousQuantity, nextQuantity);
        transfer.setStatus(StockTransferStatus.RECEIVED);
        transfer.setReceivedByUsername(actor);
        cloudTransfers.saveAndFlush(transfer);
        refreshRequestFulfillment(transfer.getRequestId());
    }

    public SyncResult syncHospital(Long hospitalId) {
        LocalStockTransferRepository local = localTransfersProvider.getIfAvailable();
        if (local == null) return new SyncResult(0, 0, 0, "LOCAL_SYNC_DISABLED");

        int pushed = 0;
        int pulled = 0;
        try {
            if (!schema.ensureTables()) throw new IllegalStateException("Cloud is unavailable.");

            for (LocalStockTransfer item : local.findByPendingSyncTrueOrderByCreatedAtAsc()) {
                if (!hospitalInTransferScope(item, hospitalId)) continue;
                StockTransfer cloud = item.getCloudTransferId() == null
                        ? new StockTransfer()
                        : cloudTransfers.findById(item.getCloudTransferId()).orElseGet(StockTransfer::new);
                boolean existing = cloud.getTransferId() != null;
                copyToCloud(item, cloud);
                if (!existing) cloud.setTransferId(null);
                cloud = cloudTransfers.saveAndFlush(cloud);
                copyFromCloud(cloud, item);
                item.setPendingSync(false);
                local.save(item);
                pushed++;
            }

            List<StockTransfer> remote = cloudTransfers.findBySourceHospitalIdOrDestinationHospitalIdOrderByCreatedAtDesc(hospitalId, hospitalId);
            for (StockTransfer cloud : remote) {
                LocalStockTransfer item = local.findByCloudTransferId(cloud.getTransferId()).orElseGet(LocalStockTransfer::new);
                if (item.isPendingSync()) continue;
                copyFromCloud(cloud, item);
                item.setPendingSync(false);
                local.save(item);
                if (cloud.getStatus() == StockTransferStatus.RECEIVED) {
                    refreshRequestFulfillment(cloud.getRequestId());
                }
                pulled++;
            }

            return new SyncResult(pushed, pulled, local.findByPendingSyncTrueOrderByCreatedAtAsc().size(), "SYNCED");
        } catch (RuntimeException ex) {
            return new SyncResult(0, 0, local.findByPendingSyncTrueOrderByCreatedAtAsc().size(),
                    "OFFLINE: " + ex.getClass().getSimpleName());
        }
    }

    public SyncResult syncKnownHospitals() {
        LocalOfflineStore store = localStoreProvider.getIfAvailable();
        if (store == null) return new SyncResult(0, 0, 0, "LOCAL_SYNC_DISABLED");
        int pushed = 0, pulled = 0;
        String status = "SYNCED";
        for (Hospital h : store.hospitals()) {
            SyncResult r = syncHospital(h.getHospitalId());
            pushed += r.pushed();
            pulled += r.pulled();
            if (r.status().startsWith("OFFLINE:")) status = r.status();
        }
        LocalStockTransferRepository local = localTransfersProvider.getIfAvailable();
        int pending = local == null ? 0 : local.findByPendingSyncTrueOrderByCreatedAtAsc().size();
        return new SyncResult(pushed, pulled, pending, status);
    }

    private void tryCloudUpdate(LocalStockTransfer local) {
        try {
            if (!schema.ensureTables()) return;
            StockTransfer cloud = local.getCloudTransferId() == null
                    ? new StockTransfer()
                    : cloudTransfers.findById(local.getCloudTransferId()).orElseGet(StockTransfer::new);
            copyToCloud(local, cloud);
            cloud = cloudTransfers.saveAndFlush(cloud);
            copyFromCloud(cloud, local);
            local.setPendingSync(false);
            LocalStockTransferRepository repo = localTransfersProvider.getIfAvailable();
            if (repo != null) repo.save(local);
        } catch (RuntimeException ignored) {
            // Local action remains authoritative until the scheduler retries.
        }
    }

    private void refreshRequestFulfillment(Long requestId) {
        try {
            if (!schema.ensureTables()) return;
            MedicineRequest request = cloudRequests.findById(requestId).orElse(null);
            if (request == null) return;
            int received = cloudTransfers.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
                    .filter(t -> t.getStatus() == StockTransferStatus.RECEIVED)
                    .mapToInt(StockTransfer::getQuantity)
                    .sum();
            request.setFulfilledQuantity(Math.min(request.getRequestedQuantity(), received));
            if (received >= request.getRequestedQuantity()) {
                request.setStatus(MedicineRequestStatus.FULFILLED);
            } else if (received > 0) {
                request.setStatus(MedicineRequestStatus.PARTIALLY_FULFILLED);
            }
            request.setUpdatedAt(LocalDateTime.now());
            cloudRequests.saveAndFlush(request);

            LocalMedicineRequestRepository local = localRequestsProvider.getIfAvailable();
            if (local != null) {
                local.findByCloudRequestId(requestId).ifPresent(item -> {
                    copyRequestToLocal(request, item);
                    item.setPendingSync(false);
                    local.save(item);
                });
            }
        } catch (RuntimeException ignored) {
            // Request status can be refreshed on the next cloud sync.
        }
    }

    private void copyRequestToLocal(MedicineRequest request, LocalMedicineRequest item) {
        item.setCloudRequestId(request.getRequestId());
        item.setHospitalId(request.getHospitalId());
        item.setDistrictId(request.getDistrictId());
        item.setStateId(request.getStateId());
        item.setMedicineId(request.getMedicineId());
        item.setRequestedQuantity(request.getRequestedQuantity());
        item.setFulfilledQuantity(request.getFulfilledQuantity());
        item.setStatus(request.getStatus().name());
        item.setRequestedByUsername(request.getRequestedByUsername());
        item.setLastUpdatedByUsername(request.getLastUpdatedByUsername());
        item.setDistrictNote(request.getDistrictNote());
        item.setStateNote(request.getStateNote());
        item.setCreatedAt(request.getCreatedAt());
        item.setUpdatedAt(request.getUpdatedAt());
    }

    private boolean hospitalInTransferScope(LocalStockTransfer t, Long hospitalId) {
        return hospitalId.equals(t.getSourceHospitalId()) || hospitalId.equals(t.getDestinationHospitalId());
    }

    private LocalStockTransfer resolveLocalTransfer(LocalStockTransferRepository repo, Long id) {
        return repo.findByCloudTransferId(id).orElseGet(() ->
                repo.findById(id).orElse(null));
    }

    private StockTransfer cloudTransfer(Long id) {
        if (!schema.ensureTables()) throw new IllegalStateException("Cloud is unavailable.");
        return cloudTransfers.findById(id).orElseThrow(() -> new IllegalArgumentException("Transfer not found."));
    }

    private MedicineRequest findCloudRequest(Long id) {
        try {
            if (!schema.ensureTables()) return null;
            return cloudRequests.findById(id).orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean hospitalInDistrict(Long hospitalId, Long districtId) {
        return cloudHospitals.findById(hospitalId).map(h -> districtId.equals(h.getDistrictId())).orElse(false);
    }

    private List<TransferView> localViewsForScope(Long districtId) {
        LocalStockTransferRepository local = localTransfersProvider.getIfAvailable();
        LocalOfflineStore store = localStoreProvider.getIfAvailable();
        if (local == null || store == null) return List.of();
        return local.findAll().stream()
                .filter(t -> store.findHospital(t.getSourceHospitalId()).map(h -> districtId.equals(h.getDistrictId())).orElse(false)
                        || store.findHospital(t.getDestinationHospitalId()).map(h -> districtId.equals(h.getDistrictId())).orElse(false))
                .sorted(Comparator.comparing(LocalStockTransfer::getCreatedAt).reversed())
                .map(this::view).toList();
    }

    private StockTransfer newTransfer(MedicineRequest request, Long sourceHospitalId, int quantity,
                                      String actor, String note) {
        StockTransfer t = new StockTransfer();
        t.setRequestId(request.getRequestId());
        t.setSourceHospitalId(sourceHospitalId);
        t.setDestinationHospitalId(request.getHospitalId());
        t.setMedicineId(request.getMedicineId());
        t.setQuantity(quantity);
        t.setStatus(StockTransferStatus.APPROVED);
        t.setCreatedByUsername(actor);
        t.setNote(note == null || note.isBlank() ? null : note.trim());
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return t;
    }

    private void copyToCloud(LocalStockTransfer local, StockTransfer cloud) {
        cloud.setTransferId(local.getCloudTransferId());
        cloud.setRequestId(local.getRequestId());
        cloud.setSourceHospitalId(local.getSourceHospitalId());
        cloud.setDestinationHospitalId(local.getDestinationHospitalId());
        cloud.setMedicineId(local.getMedicineId());
        cloud.setQuantity(local.getQuantity());
        cloud.setStatus(parseStatus(local.getStatus()));
        cloud.setCreatedByUsername(local.getCreatedByUsername());
        cloud.setDispatchedByUsername(local.getDispatchedByUsername());
        cloud.setReceivedByUsername(local.getReceivedByUsername());
        cloud.setNote(local.getNote());
        cloud.setCreatedAt(local.getCreatedAt());
        cloud.setUpdatedAt(local.getUpdatedAt());
    }

    private void copyFromCloud(StockTransfer cloud, LocalStockTransfer local) {
        local.setCloudTransferId(cloud.getTransferId());
        local.setRequestId(cloud.getRequestId());
        local.setSourceHospitalId(cloud.getSourceHospitalId());
        local.setDestinationHospitalId(cloud.getDestinationHospitalId());
        local.setMedicineId(cloud.getMedicineId());
        local.setQuantity(cloud.getQuantity());
        local.setStatus(cloud.getStatus().name());
        local.setCreatedByUsername(cloud.getCreatedByUsername());
        local.setDispatchedByUsername(cloud.getDispatchedByUsername());
        local.setReceivedByUsername(cloud.getReceivedByUsername());
        local.setNote(cloud.getNote());
        local.setCreatedAt(cloud.getCreatedAt());
        local.setUpdatedAt(cloud.getUpdatedAt());
    }

    private void copyToLocal(StockTransfer cloud, LocalStockTransfer local) {
        copyFromCloud(cloud, local);
    }

    private void authorizeSource(Long source, Long hospital) {
        if (!source.equals(hospital)) throw new IllegalArgumentException("This transfer belongs to another source hospital.");
    }

    private void authorizeDestination(Long destination, Long hospital) {
        if (!destination.equals(hospital)) throw new IllegalArgumentException("This transfer belongs to another destination hospital.");
    }

    private boolean isTransferable(MedicineRequestStatus status) {
        return status == MedicineRequestStatus.APPROVED
                || status == MedicineRequestStatus.PARTIALLY_FULFILLED
                || status == MedicineRequestStatus.STATE_APPROVED
                || status == MedicineRequestStatus.STATE_PARTIALLY_FULFILLED;
    }

    private StockTransferStatus parseStatus(String value) {
        return StockTransferStatus.valueOf(value);
    }

    private TransferView view(StockTransfer t) {
        String source = cloudHospitals.findById(t.getSourceHospitalId()).map(Hospital::getName).orElse("Hospital #" + t.getSourceHospitalId());
        String destination = cloudHospitals.findById(t.getDestinationHospitalId()).map(Hospital::getName).orElse("Hospital #" + t.getDestinationHospitalId());
        return new TransferView(t.getTransferId(), t.getRequestId(), source, destination,
                t.getMedicineId(), t.getQuantity(), t.getStatus().name(), t.getCreatedAt(), t.getUpdatedAt(), false);
    }

    private TransferView view(LocalStockTransfer t) {
        LocalOfflineStore store = localStoreProvider.getIfAvailable();
        String source = store == null ? "Hospital #" + t.getSourceHospitalId()
                : store.findHospital(t.getSourceHospitalId()).map(Hospital::getName).orElse("Hospital #" + t.getSourceHospitalId());
        String destination = store == null ? "Hospital #" + t.getDestinationHospitalId()
                : store.findHospital(t.getDestinationHospitalId()).map(Hospital::getName).orElse("Hospital #" + t.getDestinationHospitalId());
        return new TransferView(t.getLocalTransferId(), t.getRequestId(), source, destination,
                t.getMedicineId(), t.getQuantity(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt(), t.isPendingSync());
    }

    public record CreateResult(Long id, boolean localPending, String message) {}
    public record SyncResult(int pushed, int pulled, int pending, String status) {}
    public record TransferView(Long id, Long requestId, String sourceHospital, String destinationHospital,
                               Long medicineId, int quantity, String status,
                               LocalDateTime createdAt, LocalDateTime updatedAt, boolean localPending) {}
}
