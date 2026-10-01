package com.pulse.service;

import com.pulse.exception.InvalidLoginException;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.User;
import com.pulse.repository.UserRepository;
import com.pulse.security.LocalNodeSecurityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** Authentication facade with an explicitly gated hospital-local offline path. */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final BCryptPasswordEncoder encoder;
    private final LocalNodeSecurityService localNodeSecurity;

    public LoginService(UserRepository userRepository,
                        ObjectProvider<LocalOfflineStore> localStoreProvider,
                        BCryptPasswordEncoder encoder,
                        LocalNodeSecurityService localNodeSecurity) {
        this.userRepository = userRepository;
        this.localStoreProvider = localStoreProvider;
        this.encoder = encoder;
        this.localNodeSecurity = localNodeSecurity;
    }

    public User authenticate(String username, String password, String remoteAddress) {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();
        boolean trustedLocalRequest = local != null && localNodeSecurity.isTrustedLocalAddress(remoteAddress);

        // The local credential mirror is usable only from the hospital LAN/loopback.
        // Public/cloud requests never authenticate against cached local credentials.
        if (trustedLocalRequest) {
            try {
                return local.authenticateOffline(username, password, encoder);
            } catch (RuntimeException ignored) {
                // A newly-created cloud account may not have reached the mirror yet.
            }
        }

        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new InvalidLoginException("User not found: " + username));
            if (!user.isEnabled()) throw new InvalidLoginException("Account is not authorized");
            if (!encoder.matches(password, user.getPassword())) {
                throw new InvalidLoginException("Wrong password");
            }
            if (trustedLocalRequest) local.mirrorUser(user);
            return user;
        } catch (RuntimeException ex) {
            // Only a trusted hospital-local request may fall back to the offline mirror.
            if (trustedLocalRequest) {
                try {
                    return local.authenticateOffline(username, password, encoder);
                } catch (RuntimeException ignored) {
                    // Preserve the normal login error below.
                }
            }
            if (ex instanceof InvalidLoginException ile) throw ile;
            throw new InvalidLoginException("P.U.L.S.E could not authenticate this account");
        }
    }
}
