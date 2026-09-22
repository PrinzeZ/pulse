package com.pulse.repository;

import com.pulse.model.District;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DistrictRepository extends JpaRepository<District, Long> {
    List<District> findByStateIdOrderByName(Long stateId);
    Optional<District> findByNameIgnoreCaseAndStateId(String name, Long stateId);
}
