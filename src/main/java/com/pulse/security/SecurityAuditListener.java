package com.pulse.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Security events are logged without passwords, tokens, or session identifiers. */
@Component
public class SecurityAuditListener {
    private static final Logger log = LoggerFactory.getLogger("PULSE_SECURITY_AUDIT");

    @EventListener
    public void authenticationSucceeded(AuthenticationSuccessEvent event) {
        log.info("AUTH_SUCCESS username={} authorities={}",
                safe(event.getAuthentication().getName()), event.getAuthentication().getAuthorities());
    }

    @EventListener
    public void authenticationFailed(AbstractAuthenticationFailureEvent event) {
        log.warn("AUTH_FAILURE username={} reason={}",
                safe(event.getAuthentication().getName()), event.getException().getClass().getSimpleName());
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9._@-]", "_");
    }
}
