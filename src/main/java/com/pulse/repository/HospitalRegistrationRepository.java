package com.pulse.repository;

import com.pulse.model.HospitalRegistration;
import com.pulse.model.HospitalRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HospitalRegistrationRepository extends JpaRepository<HospitalRegistration, Long> {
    Optional<HospitalRegistration> findByVerificationToken(String verificationToken);
    List<HospitalRegistration> findByDistrictIdOrderByCreatedAtDesc(Long districtId);

    List<HospitalRegistration> findByDistrictIdAndStatusOrderByCreatedAtAsc(
            Long districtId, HospitalRegistrationStatus status);
    boolean existsByAdminUsername(String adminUsername);
    boolean existsByHospitalEmailAndStatus(String hospitalEmail, HospitalRegistrationStatus status);
    Optional<HospitalRegistration> findTopByHospitalEmailOrderByCreatedAtDesc(String hospitalEmail);
}
