package com.pulse.repository;

import com.pulse.model.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findByDistrict(String district);
    Optional<Hospital> findByGovernmentHospitalKey(String governmentHospitalKey);
    boolean existsByGovernmentHospitalKey(String governmentHospitalKey);

    @Query(value = """
            SELECT hospital_id AS "hospitalId",
                   name AS "name",
                   district AS "district",
                   district_id AS "districtId",
                   government_hospital_key AS "governmentHospitalKey",
                   latitude AS "latitude",
                   longitude AS "longitude"
            FROM hospitals
            ORDER BY name
            """, nativeQuery = true)
    List<HospitalMapRow> findMapRows();

    interface HospitalMapRow {
        Long getHospitalId();
        String getName();
        String getDistrict();
        Long getDistrictId();
        String getGovernmentHospitalKey();
        Double getLatitude();
        Double getLongitude();
    }
}