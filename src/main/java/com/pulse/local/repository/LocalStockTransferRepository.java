package com.pulse.local.repository;

import com.pulse.local.model.LocalStockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocalStockTransferRepository extends JpaRepository<LocalStockTransfer, Long> {
    Optional<LocalStockTransfer> findByCloudTransferId(Long cloudTransferId);
    List<LocalStockTransfer> findByPendingSyncTrueOrderByCreatedAtAsc();
    List<LocalStockTransfer> findBySourceHospitalIdOrDestinationHospitalIdOrderByCreatedAtDesc(Long sourceHospitalId, Long destinationHospitalId);
    List<LocalStockTransfer> findBySourceHospitalIdAndStatusOrderByCreatedAtDesc(Long sourceHospitalId, String status);
    List<LocalStockTransfer> findByDestinationHospitalIdAndStatusOrderByCreatedAtDesc(Long destinationHospitalId, String status);
}
