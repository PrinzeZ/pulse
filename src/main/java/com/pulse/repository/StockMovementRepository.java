package com.pulse.repository;

import com.pulse.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Optional<StockMovement> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
    List<StockMovement> findByHospitalIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscMovementIdAsc(Long hospitalId, LocalDateTime from, LocalDateTime to);
    List<StockMovement> findByHospitalIdAndOccurredAtBetweenOrderByOccurredAtAscMovementIdAsc(Long hospitalId, LocalDateTime from, LocalDateTime to);
    List<StockMovement> findByHospitalIdOrderByOccurredAtAscMovementIdAsc(Long hospitalId);
    void deleteByOccurredAtBefore(LocalDateTime cutoff);
}
