package com.pulse.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Allows a secure Railway page to probe the local HTTP P.U.L.S.E server.
 * Modern Chromium browsers use Private Network Access (PNA) for this case.
 */
@Configuration
public class PrivateNetworkAccessConfig extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String requestedPrivateNetwork = request.getHeader("Access-Control-Request-Private-Network");

        if (origin != null && !origin.isBlank()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
        }

        // Chrome sends this header on the PNA preflight from Railway HTTPS -> LAN HTTP.
        if ("true".equalsIgnoreCase(requestedPrivateNetwork)) {
            response.setHeader("Access-Control-Allow-Private-Network", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "*");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                && "true".equalsIgnoreCase(requestedPrivateNetwork)) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
