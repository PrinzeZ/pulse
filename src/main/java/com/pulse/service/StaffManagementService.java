package com.pulse.service;

import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Hospital;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffManagementService {

    private final UserRepository users;
    private final HospitalRepository hospitals;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public StaffManagementService(UserRepository users,
                                  HospitalRepository hospitals,
                                  BCryptPasswordEncoder passwordEncoder,
                                  ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.users = users;
        this.hospitals = hospitals;
        this.passwordEncoder = passwordEncoder;
        this.localStoreProvider = localStoreProvider;
    }

    public List<User> staffForHospital(Long hospitalId) {
        try {
            return users.findByHospitalIdOrderByNameAsc(hospitalId).stream()
                    .filter(user -> user instanceof PharmacyStaff)
                    .toList();
        } catch (RuntimeException ex) {
            LocalOfflineStore local = localStoreProvider.getIfAvailable();
            return local == null ? List.of() : local.hospitals().stream()
                    .filter(h -> hospitalId.equals(h.getHospitalId()))
                    .flatMap(h -> localUsers(hospitalId).stream())
                    .toList();
        }
    }

    private List<User> localUsers(Long hospitalId) {
        // LocalOfflineStore exposes staff through its cached username records via this helper.
        return localStoreProvider.getIfAvailable() == null ? List.of() :
                localStoreProvider.getIfAvailable().staffForHospital(hospitalId);
    }

    @Transactional
    public void createStaff(Long hospitalId, Long stateId, Long districtId,
                            String name, String username, String password,
                            String confirmation, boolean authorize) {
        if (hospitalId == null) throw new IllegalArgumentException("Hospital is required");
        Hospital hospital = null;
        try { hospital = hospitals.findById(hospitalId).orElse(null); } catch (RuntimeException ignored) {}
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        if (hospital == null && local != null) hospital = local.findHospital(hospitalId).orElse(null);
        if (hospital == null) throw new IllegalArgumentException("Hospital not found locally");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Staff name is required");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        username = username.trim();
        try {
            if (users.findByUsername(username).isPresent()) throw new IllegalArgumentException("Username is already in use");
        } catch (RuntimeException ex) {
            if (local != null && local.findUser(username).isPresent()) throw new IllegalArgumentException("Username is already in use");
        }
        if (password == null || password.length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters");
        if (!password.equals(confirmation)) throw new IllegalArgumentException("Passwords do not match");

        String encoded = passwordEncoder.encode(password);
        try {
            PharmacyStaff staff = new PharmacyStaff();
            staff.setName(name.trim());
            staff.setUsername(username);
            staff.setPassword(encoded);
            staff.setStateId(stateId);
            staff.setDistrictId(districtId != null ? districtId : hospital.getDistrictId());
            staff.setHospitalId(hospitalId);
            staff.setEnabled(authorize);
            User saved = users.saveAndFlush(staff);
            if (local != null) local.mirrorUser(saved);
        } catch (RuntimeException cloudFailure) {
            if (local == null) throw cloudFailure;
            local.createPendingStaff(stateId, districtId != null ? districtId : hospital.getDistrictId(),
                    hospitalId, name.trim(), username, encoded, authorize);
        }
    }

    @Transactional
    public void setAuthorized(Long hospitalId, Long staffId, boolean authorized) {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        if (local != null && local.isPendingLocalUser(staffId)) {
            local.setStaffAuthorized(hospitalId, staffId, authorized);
            return;
        }
        try {
            User user = users.findById(staffId)
                    .orElseThrow(() -> new IllegalArgumentException("Staff account not found"));
            if (!(user instanceof PharmacyStaff) || !hospitalId.equals(user.getHospitalId())) {
                throw new IllegalArgumentException("Staff account is outside your hospital");
            }
            user.setEnabled(authorized);
            users.saveAndFlush(user);
            LocalOfflineStore cachedLocal = localStoreProvider.getIfAvailable();
            if (cachedLocal != null) cachedLocal.mirrorUser(user);
            return;
        } catch (RuntimeException cloudFailure) {
            LocalOfflineStore offlineLocal = localStoreProvider.getIfAvailable();
            if (offlineLocal == null) throw cloudFailure;
            offlineLocal.setStaffAuthorized(hospitalId, staffId, authorized);   
        }
    }
}
