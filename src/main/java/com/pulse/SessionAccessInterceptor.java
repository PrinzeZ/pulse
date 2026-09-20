package com.pulse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class SessionAccessInterceptor implements HandlerInterceptor {

    private final SessionSecurity sessionSecurity;

    public SessionAccessInterceptor(SessionSecurity sessionSecurity) {
        this.sessionSecurity = sessionSecurity;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        HttpSession session = request.getSession(false);
        if (path.equals("/admin") || path.startsWith("/admin/")) {
            return requireRole(response, request, session, "ADMIN");
        }
        if (path.equals("/staff") || path.startsWith("/staff/")) {
            return requireRole(response, request, session, "STAFF");
        }
        return true;
    }

    private boolean requireRole(HttpServletResponse response, HttpServletRequest request, HttpSession session, String role) throws IOException {
        if (sessionSecurity.hasRole(session, role)) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }
}
