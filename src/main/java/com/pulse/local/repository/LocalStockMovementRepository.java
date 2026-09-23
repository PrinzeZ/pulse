package com.pulse.local.repository;

import com.pulse.local.model.LocalStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface LocalStockMovementRepository extends JpaRepository<LocalStockMovement, Long> {
    Optional<LocalStockMovement> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
    List<LocalStockMovement> findByHospitalIdOrderByOccurredAtAscLocalMovementIdAsc(Long hospitalId);
    List<LocalStockMovement> findByPendingSyncTrueOrderByOccurredAtAsc();
    List<LocalStockMovement> findByHospitalIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscLocalMovementIdAsc(Long hospitalId, LocalDateTime from, LocalDateTime to);
    void deleteByOccurredAtBeforeAndPendingSyncFalse(LocalDateTime cutoff);
}
