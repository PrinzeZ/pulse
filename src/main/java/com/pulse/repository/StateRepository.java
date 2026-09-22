package com.pulse.repository;
import java.util.Optional;

import com.pulse.model.State;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateRepository extends JpaRepository<State, Long> {
    Optional<State> findByNameIgnoreCase(String name);
}
