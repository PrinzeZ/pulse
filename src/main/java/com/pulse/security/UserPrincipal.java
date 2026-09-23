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
 * Small serializable identity snapshot kept in the Spring Security session.
 * The JPA User entity itself is deliberately not stored in the security context.
 */
public final class UserPrincipal implements UserDetails {
    private final Long userId;
    private final String name;
    private final String username;
    private final String password;
    private final String role;
    private final Long stateId;
    private final Long districtId;
    private final Long hospitalId;
    private final boolean enabled;

    private UserPrincipal(Long userId, String name, String username, String password,
                          String role, Long stateId, Long districtId, Long hospitalId,
                          boolean enabled) {
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.password = password;
        this.role = role;
        this.stateId = stateId;
        this.districtId = districtId;
        this.hospitalId = hospitalId;
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getUserId(), user.getName(), user.getUsername(), user.getPassword(),
                roleOf(user), user.getStateId(), user.getDistrictId(), user.getHospitalId(),
                user.isEnabled());
    }

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
        user.setPassword(password);
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

    @Override public String getPassword() { return password; }
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
