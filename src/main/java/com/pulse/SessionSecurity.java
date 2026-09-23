package com.pulse;

import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
import com.pulse.security.UserPrincipal;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SessionSecurity {
    public static final String ROLE = "role"; // legacy view compatibility
    public static final String LOGGED_IN_USER = "loggedInUser"; // legacy key; authentication is now authoritative

    public boolean isAuthenticated(HttpSession session) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal()));
    }

    public boolean hasRole(HttpSession session, String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    public User getUser(HttpSession session) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        Object principal = authentication.getPrincipal();
        return principal instanceof UserPrincipal userPrincipal ? userPrincipal.toUser() : null;
    }

    public PharmacyStaff getStaff(HttpSession session) {
        User user = getUser(session);
        return user instanceof PharmacyStaff staff ? staff : null;
    }
}
