package com.pulse.local.repository;

import com.pulse.local.model.LocalMedicineRequestActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LocalMedicineRequestActionLogRepository extends JpaRepository<LocalMedicineRequestActionLog, Long> {
    Optional<LocalMedicineRequestActionLog> findByEventId(String eventId);
    List<LocalMedicineRequestActionLog> findByLocalRequestIdOrderByOccurredAtAsc(Long localRequestId);
    List<LocalMedicineRequestActionLog> findByHospitalIdOrderByOccurredAtDesc(Long hospitalId);
    List<LocalMedicineRequestActionLog> findByPendingSyncTrueOrderByOccurredAtAsc();
    Optional<LocalMedicineRequestActionLog> findTopByOrderByOccurredAtDescActionLogIdDesc();
    List<LocalMedicineRequestActionLog> findByHospitalIdAndOccurredAtBetweenOrderByOccurredAtAsc(Long hospitalId, java.time.LocalDateTime start, java.time.LocalDateTime end);
    void deleteByOccurredAtBeforeAndPendingSyncFalse(java.time.LocalDateTime cutoff);
}
