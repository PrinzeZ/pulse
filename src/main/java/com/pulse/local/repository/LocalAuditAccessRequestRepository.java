package com.pulse.local.repository;

import com.pulse.local.model.LocalAuditAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocalAuditAccessRequestRepository extends JpaRepository<LocalAuditAccessRequest, Long> {
    List<LocalAuditAccessRequest> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
