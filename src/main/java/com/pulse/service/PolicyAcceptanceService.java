package com.pulse.service;

import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.User;
import com.pulse.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Records the policy version acknowledged during authentication.
 *
 * Authentication is only allowed to complete when the acknowledgement is
 * durably recorded in at least one authoritative store: the cloud user record
 * or the hospital-local credential mirror for an offline hospital login.
 */
@Service
public class PolicyAcceptanceService {
    public static final String POLICY_VERSION = "2026-10-01";

    private final UserRepository users;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;

    public PolicyAcceptanceService(UserRepository users, ObjectProvider<LocalOfflineStore> localStoreProvider) {
        this.users = users;
        this.localStoreProvider = localStoreProvider;
    }

    public boolean recordForUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required to record policy acceptance");
        }

        String normalizedUsername = username.trim();
        LocalDateTime acceptedAt = LocalDateTime.now();
        boolean recorded = false;

        try {
            User user = users.findByUsername(normalizedUsername).orElse(null);
            if (user != null) {
                user.setPolicyVersion(POLICY_VERSION);
                user.setPolicyAcceptedAt(acceptedAt);
                users.saveAndFlush(user);
                recorded = true;
            }
        } catch (RuntimeException cloudFailure) {
            // A hospital-local login may legitimately be offline. The local
            // credential mirror below is the durable fallback in that case.
        }

        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        if (local != null) {
            try {
                int updated = local.recordPolicyAcceptance(normalizedUsername, POLICY_VERSION, acceptedAt);
                if (updated > 0) recorded = true;
            } catch (RuntimeException localFailure) {
                // A successful cloud write is already durable. A local mirror failure
                // must not invalidate an otherwise successful cloud authentication.
                if (!recorded) throw localFailure;
            }
        }

        if (!recorded) {
            throw new IllegalStateException("Policy acknowledgement could not be durably recorded");
        }
        return true;
    }
}
