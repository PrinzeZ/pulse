package com.pulse.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** Login-request details that carry the mandatory policy acknowledgement. */
public final class PulseWebAuthenticationDetails extends WebAuthenticationDetails {
    private final boolean policyAccepted;

    public PulseWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.policyAccepted = "true".equalsIgnoreCase(request.getParameter("policyAccepted"));
    }

    public boolean isPolicyAccepted() {
        return policyAccepted;
    }
}
