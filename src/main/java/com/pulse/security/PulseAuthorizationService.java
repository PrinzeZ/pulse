package com.pulse.security;

import com.pulse.model.District;
import com.pulse.model.Hospital;
import com.pulse.repository.DistrictRepository;
import com.pulse.repository.HospitalRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("pulseAuthorization")
public class PulseAuthorizationService {
    private final HospitalRepository hospitals;
    private final DistrictRepository districts;

    public PulseAuthorizationService(HospitalRepository hospitals, DistrictRepository districts) {
        this.hospitals = hospitals;
        this.districts = districts;
    }

    public boolean hasHospitalScope() {
        UserPrincipal principal = principal();
        return principal != null
                && principal.getHospitalId() != null
                && ("ADMIN".equals(principal.getRole()) || "STAFF".equals(principal.getRole()));
    }

    public boolean hasDistrictScope() {
        UserPrincipal principal = principal();
        return principal != null && principal.getDistrictId() != null
                && ("DISTRICT_ADMIN".equals(principal.getRole()) || "STATE_ADMIN".equals(principal.getRole()));
    }

    public boolean hasStateScope() {
        UserPrincipal principal = principal();
        return principal != null && principal.getStateId() != null
                && "STATE_ADMIN".equals(principal.getRole());
    }

    public boolean canAccessHospital(Long hospitalId) {
        if (hospitalId == null) return false;
        UserPrincipal principal = principal();
        if (principal == null) return false;
        if ("ADMIN".equals(principal.getRole()) || "STAFF".equals(principal.getRole())) {
            return hospitalId.equals(principal.getHospitalId());
        }
        Hospital hospital;
        try {
            hospital = hospitals.findById(hospitalId).orElse(null);
        } catch (RuntimeException ex) {
            return false;
        }
        if (hospital == null) return false;
        if ("DISTRICT_ADMIN".equals(principal.getRole())) {
            return principal.getDistrictId() != null && principal.getDistrictId().equals(hospital.getDistrictId());
        }
        if ("STATE_ADMIN".equals(principal.getRole())) {
            return principal.getStateId() != null && hospitalInState(hospital, principal.getStateId());
        }
        return false;
    }

    public boolean canAccessDistrict(Long districtId) {
        if (districtId == null) return false;
        UserPrincipal principal = principal();
        if (principal == null) return false;
        if ("DISTRICT_ADMIN".equals(principal.getRole())) {
            return districtId.equals(principal.getDistrictId());
        }
        if ("STATE_ADMIN".equals(principal.getRole())) {
            try {
                return districts.findById(districtId)
                        .map(d -> principal.getStateId() != null && principal.getStateId().equals(d.getStateId()))
                        .orElse(false);
            } catch (RuntimeException ex) {
                return false;
            }
        }
        return false;
    }

    public boolean canAccessState(Long stateId) {
        UserPrincipal principal = principal();
        return principal != null && "STATE_ADMIN".equals(principal.getRole())
                && stateId != null && stateId.equals(principal.getStateId());
    }

    private boolean hospitalInState(Hospital hospital, Long stateId) {
        if (hospital.getDistrictId() == null) return false;
        try {
            return districts.findById(hospital.getDistrictId())
                    .map(d -> stateId.equals(d.getStateId()))
                    .orElse(false);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private UserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return authentication.getPrincipal() instanceof UserPrincipal p ? p : null;
    }
}
