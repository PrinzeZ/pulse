package com.pulse.repository;

import com.pulse.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByResolvedFalseOrderByCreatedAtDesc();
    List<Alert> findByHospitalIdAndMedIdAndResolvedFalse(Long hospitalId, Long medicineId);
}