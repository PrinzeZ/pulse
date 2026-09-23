package com.pulse.config;

import com.pulse.security.PulseAccessDeniedHandler;
import com.pulse.security.UserPrincipal;
import com.pulse.local.service.StockSyncService;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpSession;
import com.pulse.security.PulseAuthenticationProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final PulseAuthenticationProvider authenticationProvider;
    private final PulseAccessDeniedHandler accessDeniedHandler;
    private final ObjectProvider<StockSyncService> stockSyncServiceProvider;

    public SecurityConfig(PulseAuthenticationProvider authenticationProvider,
                          PulseAccessDeniedHandler accessDeniedHandler,
                          ObjectProvider<StockSyncService> stockSyncServiceProvider) {
        this.authenticationProvider = authenticationProvider;
        this.accessDeniedHandler = accessDeniedHandler;
        this.stockSyncServiceProvider = stockSyncServiceProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    AuthenticationSuccessHandler successHandler,
                                                    AuthenticationFailureHandler failureHandler) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        http
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/error", "/access-denied",
                    "/search", "/medicines", "/user", "/map", "/medicine/**",
                    "/hospital/**", "/api/public/**", "/api/map/**",
                    "/health", "/status", "/lan",
                    "/css/**", "/js/**", "/images/**", "/favicon.ico"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/staff/**").hasRole("STAFF")
                .requestMatchers("/district-admin/**").hasRole("DISTRICT_ADMIN")
                .requestMatchers("/state-admin/**").hasRole("STATE_ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/logout"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(csrfHandler)
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(2)
                .maxSessionsPreventsLogin(false)
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(accessDeniedHandler)
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendRedirect(request.getContextPath() + "/login"))
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(false)
                    .maxAgeInSeconds(31536000))
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.setStatus(HttpServletResponse.SC_FOUND);
            String role = authentication.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .findFirst()
                    .orElse("");
            // Transitional compatibility for existing controllers/views.
            // Authorization itself is enforced by Spring Security authorities.
            if (authentication.getPrincipal() instanceof UserPrincipal principal) {
                HttpSession session = request.getSession(true);
                session.setAttribute("role", principal.getRole());
                session.setAttribute("stateId", principal.getStateId());
                session.setAttribute("districtId", principal.getDistrictId());
                session.setAttribute("hospitalId", principal.getHospitalId());

                if ("STAFF".equals(principal.getRole()) && principal.getHospitalId() != null) {
                    StockSyncService syncService = stockSyncServiceProvider.getIfAvailable();
                    if (syncService != null) {
                        try {
                            syncService.syncHospital(principal.getHospitalId());
                        } catch (RuntimeException ignored) {
                            // Login must remain available when cloud sync is unavailable.
                        }
                    }
                }
            }
            String target = switch (role) {
                case "ROLE_STATE_ADMIN" -> "/state-admin/dashboard";
                case "ROLE_DISTRICT_ADMIN" -> "/district-admin/dashboard";
                case "ROLE_ADMIN" -> "/admin/dashboard";
                case "ROLE_STAFF" -> "/staff/dashboard";
                default -> "/login?error=true";
            };
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            response.sendRedirect(request.getContextPath() + "/login?error=true");
        };
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
