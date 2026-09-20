package com.pulse;

import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class SessionSecurity {

    public static final String ROLE = "role";
    public static final String LOGGED_IN_USER = "loggedInUser";

    public boolean isAuthenticated(HttpSession session) {
        return session != null && session.getAttribute(LOGGED_IN_USER) instanceof User;
    }

    public boolean hasRole(HttpSession session, String role) {
        return isAuthenticated(session) && role.equals(session.getAttribute(ROLE));
    }

    public User getUser(HttpSession session) {
        if (!isAuthenticated(session)) {
            return null;
        }
        return (User) session.getAttribute(LOGGED_IN_USER);
    }

    public PharmacyStaff getStaff(HttpSession session) {
        User user = getUser(session);
        return user instanceof PharmacyStaff staff ? staff : null;
    }
}
