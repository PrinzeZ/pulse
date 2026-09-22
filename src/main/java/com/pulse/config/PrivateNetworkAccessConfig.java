package com.pulse.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class PrivateNetworkAccessConfig {
    @Bean
    public Filter privateNetworkAccessFilter() {
        return (ServletRequest request, ServletResponse response, FilterChain chain) -> {
            HttpServletResponse http = (HttpServletResponse) response;
            http.setHeader("Access-Control-Allow-Private-Network", "true");
            chain.doFilter(request, response);
        };
    }
}
