package com.pulse.repository;

import com.pulse.model.HospitalAdminActivation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HospitalAdminActivationRepository extends JpaRepository<HospitalAdminActivation, Long> {
    Optional<HospitalAdminActivation> findByToken(String token);
}
