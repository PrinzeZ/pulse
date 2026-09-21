package com.pulse.config;

import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.Admin;
import com.pulse.model.DistrictAdmin;
import com.pulse.model.StateAdmin;
import com.pulse.model.User;
import com.pulse.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.boot.CommandLineRunner;

@Profile("dev")
public class Phase3AdminInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public Phase3AdminInitializer(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                                  ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.localStoreProvider = localStoreProvider;
    }

    @Override
    public void run(String... args) {
        System.out.println("=== PHASE 3 ADMIN INITIALIZER START ===");
        createAdmin("State Administrator", "state_admin", "state123", StateAdmin.class, 1L, null, null);
        createAdmin("Ernakulam District Administrator", "ernakulam_admin", "ekm123", DistrictAdmin.class, 1L, 1L, null);
        createAdmin("Kozhikode District Administrator", "kozhikode_admin", "kzd123", DistrictAdmin.class, 1L, 2L, null);
        createAdmin("Ernakulam Hospital Administrator", "ekm_hospital_admin", "ekmhospital123", Admin.class, 1L, 1L, 3L);
        createAdmin("Kozhikode Hospital Administrator", "kozhikode_hospital_admin", "kzdhospital123", Admin.class, 1L, 2L, 1L);
        try { System.out.println("DEV VERIFY: users=" + userRepository.count()); }
        catch (RuntimeException ex) { System.out.println("DEV VERIFY: cloud users unavailable; local authentication is active"); }
        System.out.println("=== PHASE 3 ADMIN INITIALIZER COMPLETE ===");
    }

    private <T extends User> void createAdmin(String name, String username, String password, Class<T> adminClass,
                                               Long stateId, Long districtId, Long hospitalId) {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        if (local != null && local.findUser(username).isPresent()) return;
        try {
            User existing = userRepository.findByUsername(username).orElse(null);
            if (existing != null) { if (local != null) local.mirrorUser(existing); return; }
            T admin = adminClass.getDeclaredConstructor().newInstance();
            admin.setName(name); admin.setUsername(username); admin.setPassword(passwordEncoder.encode(password));
            admin.setStateId(stateId); admin.setDistrictId(districtId); admin.setHospitalId(hospitalId); admin.setEnabled(true);
            User saved = userRepository.saveAndFlush(admin);
            if (local != null) local.mirrorUser(saved);
        } catch (Exception cloudFailure) {
            if (local != null) local.createPendingUser(stateId, districtId, hospitalId, name, username,
                    passwordEncoder.encode(password), roleOf(adminClass), true);
        }
    }

    private String roleOf(Class<? extends User> type) {
        if (type == StateAdmin.class) return "STATE_ADMIN";
        if (type == DistrictAdmin.class) return "DISTRICT_ADMIN";
        return "ADMIN";
    }
}
