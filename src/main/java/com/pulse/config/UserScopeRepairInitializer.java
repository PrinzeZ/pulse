package com.pulse.config;

import com.pulse.model.Admin;
import com.pulse.model.District;
import com.pulse.model.Hospital;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.repository.DistrictRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repairs legacy/dev users whose hospital scope was created before the
 * state_id/district_id columns were populated. Hospital identity is the
 * source of truth for hospital-scoped ADMIN/STAFF accounts.
 *
 * This is deliberately limited to the dev profile. Production migrations
 * should use SUPABASE_USER_SCOPE_REPAIR.sql instead of silently mutating data
 * at application startup.
 */
@Profile("dev")
@Component
@Order(100)
public class UserScopeRepairInitializer implements org.springframework.boot.CommandLineRunner {

    private final UserRepository users;
    private final HospitalRepository hospitals;
    private final DistrictRepository districts;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public UserScopeRepairInitializer(UserRepository users,
                                      HospitalRepository hospitals,
                                      DistrictRepository districts,
                                      ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.users = users;
        this.hospitals = hospitals;
        this.districts = districts;
        this.localStoreProvider = localStoreProvider;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int repaired = 0;
        try {
            for (User user : users.findAll()) {
                if (!(user instanceof Admin || user instanceof PharmacyStaff)) continue;
                if (user.getHospitalId() == null) continue;

                Hospital hospital = hospitals.findById(user.getHospitalId()).orElse(null);
                if (hospital == null || hospital.getDistrictId() == null) continue;

                District district = districts.findById(hospital.getDistrictId()).orElse(null);
                if (district == null || district.getStateId() == null) continue;

                boolean changed = false;
                if (!district.getStateId().equals(user.getStateId())) {
                    user.setStateId(district.getStateId());
                    changed = true;
                }
                if (!district.getDistrictId().equals(user.getDistrictId())) {
                    user.setDistrictId(district.getDistrictId());
                    changed = true;
                }

                if (changed) {
                    users.save(user);
                    LocalOfflineStore local = localStoreProvider.getIfAvailable();
                    if (local != null) local.mirrorUser(user);
                    repaired++;
                }
            }
            if (repaired > 0) users.flush();
            System.out.println("DEV USER SCOPE REPAIR: repaired=" + repaired);
        } catch (RuntimeException ex) {
            // Never prevent application startup because this compatibility repair
            // could not reach the cloud database. The normal authentication path
            // remains responsible for the actual login decision.
            System.out.println("DEV USER SCOPE REPAIR: skipped (database unavailable)");
        }
    }
}
