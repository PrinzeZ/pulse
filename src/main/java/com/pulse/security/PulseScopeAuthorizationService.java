package com.pulse.security;

import com.pulse.model.District;
import com.pulse.model.Hospital;
import com.pulse.model.MedicineRequest;
import com.pulse.model.StockTransfer;
import com.pulse.model.User;
import com.pulse.repository.DistrictRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRequestRepository;
import com.pulse.repository.HospitalRegistrationRepository;
import com.pulse.repository.StockTransferRepository;
import com.pulse.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Central P.U.L.S.E. resource-scope authorization layer.
 *
 * Authentication answers "who are you?". This service answers "may this
 * authenticated identity access this particular resource?". Network location
 * and session attributes are deliberately not used as proof of authorization.
 */
@Service("pulseScope")
public class PulseScopeAuthorizationService {

    private final HospitalRepository hospitals;
    private final DistrictRepository districts;
    private final MedicineRequestRepository requests;
    private final StockTransferRepository transfers;
    private final UserRepository users;
    private final HospitalRegistrationRepository registrations;

    public PulseScopeAuthorizationService(HospitalRepository hospitals,
                                          DistrictRepository districts,
                                          MedicineRequestRepository requests,
                                          StockTransferRepository transfers,
                                          UserRepository users,
                                          HospitalRegistrationRepository registrations) {
        this.hospitals = hospitals;
        this.districts = districts;
        this.requests = requests;
        this.transfers = transfers;
        this.users = users;
        this.registrations = registrations;
    }

    public boolean canAccessHospital(Long hospitalId) {
        UserPrincipal principal = principal();
        if (principal == null || hospitalId == null) return false;
        if (isRole(principal, "STATE_ADMIN")) {
            return hospitals.findById(hospitalId).map(this::hospitalInPrincipalState).orElse(false);
        }
        if (isRole(principal, "DISTRICT_ADMIN")) {
            return hospitals.findById(hospitalId)
                    .map(h -> equals(h.getDistrictId(), principal.getDistrictId()))
                    .orElse(false);
        }
        return equals(principal.getHospitalId(), hospitalId);
    }

    public boolean canAccessDistrict(Long districtId) {
        UserPrincipal principal = principal();
        if (principal == null || districtId == null) return false;
        if (isRole(principal, "STATE_ADMIN")) {
            return districts.findById(districtId)
                    .map(d -> equals(d.getStateId(), principal.getStateId()))
                    .orElse(false);
        }
        return equals(principal.getDistrictId(), districtId);
    }

    public boolean canAccessState(Long stateId) {
        UserPrincipal principal = principal();
        return principal != null && equals(principal.getStateId(), stateId);
    }

    public boolean canAccessRegistration(Long registrationId) {
        UserPrincipal principal = principal();
        if (principal == null || registrationId == null) return false;
        return registrations.findById(registrationId)
                .map(r -> switch (principal.getRole()) {
                    case "STATE_ADMIN" -> equals(r.getStateId(), principal.getStateId());
                    case "DISTRICT_ADMIN" -> equals(r.getDistrictId(), principal.getDistrictId());
                    default -> false;
                })
                .orElse(false);
    }

    public boolean canAccessRequest(Long requestId) {
        UserPrincipal principal = principal();
        if (principal == null || requestId == null) return false;
        return requests.findById(requestId).map(request -> canAccessRequest(principal, request)).orElse(false);
    }

    public boolean canAccessTransfer(Long transferId) {
        UserPrincipal principal = principal();
        if (principal == null || transferId == null) return false;
        return transfers.findById(transferId).map(t ->
                canAccessHospitalForRole(principal, t.getSourceHospitalId())
                        && canAccessHospitalForRole(principal, t.getDestinationHospitalId())
        ).orElse(false);
    }

    public boolean canDispatchTransfer(Long transferId) {
        UserPrincipal principal = principal();
        if (principal == null || transferId == null) return false;
        return transfers.findById(transferId)
                .map(t -> canAccessHospitalForRole(principal, t.getSourceHospitalId()))
                .orElse(false);
    }

    public boolean canReceiveTransfer(Long transferId) {
        UserPrincipal principal = principal();
        if (principal == null || transferId == null) return false;
        return transfers.findById(transferId)
                .map(t -> canAccessHospitalForRole(principal, t.getDestinationHospitalId()))
                .orElse(false);
    }

    public boolean canManageStaff(Long userId) {
        UserPrincipal principal = principal();
        if (principal == null || userId == null || !isRole(principal, "ADMIN")) return false;
        return users.findById(userId)
                .map(target -> equals(target.getHospitalId(), principal.getHospitalId()))
                .orElse(false);
    }

    private boolean canAccessRequest(UserPrincipal principal, MedicineRequest request) {
        return switch (principal.getRole()) {
            case "STATE_ADMIN" -> equals(request.getStateId(), principal.getStateId());
            case "DISTRICT_ADMIN" -> equals(request.getDistrictId(), principal.getDistrictId());
            case "ADMIN", "STAFF" -> equals(request.getHospitalId(), principal.getHospitalId());
            default -> false;
        };
    }

    private boolean canAccessHospitalForRole(UserPrincipal principal, Long hospitalId) {
        if (hospitalId == null) return false;
        if (isRole(principal, "STATE_ADMIN")) {
            return hospitals.findById(hospitalId).map(this::hospitalInPrincipalState).orElse(false);
        }
        if (isRole(principal, "DISTRICT_ADMIN")) {
            return hospitals.findById(hospitalId)
                    .map(h -> equals(h.getDistrictId(), principal.getDistrictId()))
                    .orElse(false);
        }
        return equals(principal.getHospitalId(), hospitalId);
    }

    private boolean hospitalInPrincipalState(Hospital hospital) {
        UserPrincipal principal = principal();
        return hospital.getDistrictId() != null
                && districts.findById(hospital.getDistrictId())
                .map(District::getStateId)
                .map(stateId -> principal != null && equals(stateId, principal.getStateId()))
                .orElse(false);
    }

    private UserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        Object value = authentication.getPrincipal();
        return value instanceof UserPrincipal p ? p : null;
    }

    private boolean isRole(UserPrincipal principal, String role) {
        return role.equals(principal.getRole());
    }

    private boolean equals(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }
}
