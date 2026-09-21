package com.pulse.repository;

import com.pulse.model.StockTransfer;
import com.pulse.model.StockTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    List<StockTransfer> findByRequestIdOrderByCreatedAtDesc(Long requestId);
    List<StockTransfer> findBySourceHospitalIdOrDestinationHospitalIdOrderByCreatedAtDesc(Long sourceHospitalId, Long destinationHospitalId);
    List<StockTransfer> findBySourceHospitalIdAndStatusOrderByCreatedAtDesc(Long sourceHospitalId, StockTransferStatus status);
    List<StockTransfer> findByDestinationHospitalIdAndStatusOrderByCreatedAtDesc(Long destinationHospitalId, StockTransferStatus status);
}
