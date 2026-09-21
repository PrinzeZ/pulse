package com.pulse.repository;

import com.pulse.model.District;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Long> {
    List<District> findByStateIdOrderByName(Long stateId);
}
