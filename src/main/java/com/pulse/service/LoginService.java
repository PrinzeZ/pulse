package com.pulse.service;

import com.pulse.exception.InvalidLoginException;
import com.pulse.local.service.LocalOfflineStore;
import com.pulse.model.User;
import com.pulse.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final ObjectProvider<LocalOfflineStore> localStoreProvider;
    private final BCryptPasswordEncoder encoder;

    public LoginService(UserRepository userRepository, ObjectProvider<LocalOfflineStore> localStoreProvider,
                        BCryptPasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.localStoreProvider = localStoreProvider;
        this.encoder = encoder;
    }

    public User authenticate(String username, String password) {
        LocalOfflineStore local = localStoreProvider.getIfAvailable();

        // On a local hospital node, the local mirror is authoritative for login.
        // This makes login independent of the Internet. Successful cloud sync keeps
        // the mirror current when connectivity is available.
        if (local != null) {
            try {
                return local.authenticateOffline(username, password, encoder);
            } catch (RuntimeException ignored) {
                // Fall through to cloud so newly-created/updated accounts can work
                // before the next local reference-data refresh.
            }
        }

        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new InvalidLoginException("User not found: " + username));
            if (!user.isEnabled()) throw new InvalidLoginException("Account is not authorized");
            if (!encoder.matches(password, user.getPassword())) {
                throw new InvalidLoginException("Wrong password");
            }
            if (local != null) local.mirrorUser(user);
            return user;
        } catch (RuntimeException ex) {
            if (local != null) {
                try {
                    return local.authenticateOffline(username, password, encoder);
                } catch (RuntimeException ignored) {
                    // Preserve the normal login error below.
                }
            }
            if (ex instanceof InvalidLoginException ile) throw ile;
            throw new InvalidLoginException("P.U.L.S.E is offline and this account is not cached locally");
        }
    }
}
