package com.pulse.security;

import com.pulse.model.Admin;
import com.pulse.model.DistrictAdmin;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.StateAdmin;
import com.pulse.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Small identity snapshot kept in the Spring Security session.
 *
 * SECURITY RULE: the password hash is deliberately NOT retained in the
 * authenticated principal. Password verification happens only inside the
 * authentication provider; the post-authentication security context carries
 * identity, role and resource scope only.
 */
public final class UserPrincipal implements UserDetails {
    private final Long userId;
    private final String name;
    private final String username;
    private final String role;
    private final Long stateId;
    private final Long districtId;
    private final Long hospitalId;
    private final boolean enabled;

    private UserPrincipal(Long userId, String name, String username,
                          String role, Long stateId, Long districtId, Long hospitalId,
                          boolean enabled) {
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.role = role;
        this.stateId = stateId;
        this.districtId = districtId;
        this.hospitalId = hospitalId;
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getUserId(), user.getName(), user.getUsername(),
                roleOf(user), user.getStateId(), user.getDistrictId(), user.getHospitalId(),
                user.isEnabled());
    }

    /**
     * Compatibility projection for legacy controllers. Never reconstructs a
     * password hash into the object graph; callers only need identity/scope.
     */
    public User toUser() {
        User user = switch (role) {
            case "STATE_ADMIN" -> new StateAdmin();
            case "DISTRICT_ADMIN" -> new DistrictAdmin();
            case "ADMIN" -> new Admin();
            case "STAFF" -> new PharmacyStaff();
            default -> throw new IllegalStateException("Unsupported P.U.L.S.E role: " + role);
        };
        user.setUserId(userId);
        user.setName(name);
        user.setUsername(username);
        user.setStateId(stateId);
        user.setDistrictId(districtId);
        user.setHospitalId(hospitalId);
        user.setEnabled(enabled);
        return user;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public Long getStateId() { return stateId; }
    public Long getDistrictId() { return districtId; }
    public Long getHospitalId() { return hospitalId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** No password or password hash is retained after authentication. */
    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }

    private static String roleOf(User user) {
        if (user instanceof StateAdmin) return "STATE_ADMIN";
        if (user instanceof DistrictAdmin) return "DISTRICT_ADMIN";
        if (user instanceof Admin) return "ADMIN";
        if (user instanceof PharmacyStaff) return "STAFF";
        throw new IllegalArgumentException("Unsupported P.U.L.S.E user type: " + user.getClass().getName());
    }
}
