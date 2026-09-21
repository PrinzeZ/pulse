package com.pulse.repository;

import com.pulse.model.MedicineRequest;
import com.pulse.model.MedicineRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MedicineRequestRepository extends JpaRepository<MedicineRequest, Long> {
    List<MedicineRequest> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    List<MedicineRequest> findByDistrictIdAndStatusInOrderByCreatedAtDesc(Long districtId, Collection<MedicineRequestStatus> statuses);
    List<MedicineRequest> findByStateIdAndStatusInOrderByCreatedAtDesc(Long stateId, Collection<MedicineRequestStatus> statuses);
}
