package com.pulse.repository;

import com.pulse.model.MedicineRequestActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MedicineRequestActionLogRepository extends JpaRepository<MedicineRequestActionLog, Long> {
    Optional<MedicineRequestActionLog> findByEventId(String eventId);
    List<MedicineRequestActionLog> findByHospitalIdOrderByOccurredAtDesc(Long hospitalId);
    List<MedicineRequestActionLog> findByDistrictIdOrderByOccurredAtDesc(Long districtId);
    List<MedicineRequestActionLog> findByStateIdOrderByOccurredAtDesc(Long stateId);
    Optional<MedicineRequestActionLog> findTopByOrderByOccurredAtDescActionLogIdDesc();
    List<MedicineRequestActionLog> findByHospitalIdAndOccurredAtBetweenOrderByOccurredAtAsc(Long hospitalId, java.time.LocalDateTime start, java.time.LocalDateTime end);
    void deleteByOccurredAtBefore(java.time.LocalDateTime cutoff);
}
