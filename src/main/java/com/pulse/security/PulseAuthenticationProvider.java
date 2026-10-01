package com.pulse.security;

import com.pulse.exception.InvalidLoginException;
import com.pulse.model.User;
import com.pulse.service.LoginService;
import com.pulse.service.PolicyAcceptanceService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class PulseAuthenticationProvider implements AuthenticationProvider {
    private final LoginService loginService;
    private final LoginAttemptService attempts;
    private final PolicyAcceptanceService policyAcceptanceService;

    public PulseAuthenticationProvider(LoginService loginService, LoginAttemptService attempts,
                                       PolicyAcceptanceService policyAcceptanceService) {
        this.loginService = loginService;
        this.attempts = attempts;
        this.policyAcceptanceService = policyAcceptanceService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName() == null ? "" : authentication.getName().trim();
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        String key = username + "|" + clientKey(authentication);

        if (authentication.getDetails() instanceof PulseWebAuthenticationDetails details
                && !details.isPolicyAccepted()) {
            throw new BadCredentialsException("You must accept the P.U.L.S.E. Terms and Conditions and Privacy Policy");
        }

        if (attempts.isBlocked(key)) {
            throw new LockedException("Too many failed sign-in attempts. Try again later.");
        }

        try {
            String remoteAddress = clientKey(authentication);
            User user = loginService.authenticate(username, password, remoteAddress);
            policyAcceptanceService.recordForUsername(user.getUsername());
            attempts.recordSuccess(key);
            UserPrincipal principal = UserPrincipal.from(user);
            return UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities());
        } catch (InvalidLoginException ex) {
            attempts.recordFailure(key);
            throw new BadCredentialsException("Invalid username or password");
        } catch (RuntimeException ex) {
            attempts.recordFailure(key);
            throw new BadCredentialsException("Unable to authenticate account");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private String clientKey(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof WebAuthenticationDetails web) {
            return web.getRemoteAddress() == null ? "unknown" : web.getRemoteAddress();
        }
        return "unknown";
    }
}
