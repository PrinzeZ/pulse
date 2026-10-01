package com.pulse.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;

@Component
public class PulseAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, PulseWebAuthenticationDetails> {
    @Override
    public PulseWebAuthenticationDetails buildDetails(HttpServletRequest context) {
        return new PulseWebAuthenticationDetails(context);
    }
}
