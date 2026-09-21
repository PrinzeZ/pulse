package com.pulse.local.repository;

import com.pulse.local.model.LocalMedicineRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocalMedicineRequestRepository extends JpaRepository<LocalMedicineRequest, Long> {
    List<LocalMedicineRequest> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    List<LocalMedicineRequest> findByHospitalIdAndPendingSyncTrueOrderByCreatedAtAsc(Long hospitalId);
    List<LocalMedicineRequest> findByDistrictIdOrderByCreatedAtDesc(Long districtId);
    List<LocalMedicineRequest> findByStateIdOrderByCreatedAtDesc(Long stateId);
    List<LocalMedicineRequest> findByPendingSyncTrueOrderByCreatedAtAsc();
    Optional<LocalMedicineRequest> findByCloudRequestId(Long cloudRequestId);
}
