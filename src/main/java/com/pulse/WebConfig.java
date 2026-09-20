package com.pulse;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionAccessInterceptor sessionAccessInterceptor;

    public WebConfig(SessionAccessInterceptor sessionAccessInterceptor) {
        this.sessionAccessInterceptor = sessionAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionAccessInterceptor)
                .addPathPatterns("/admin/**", "/staff/**");
    }
}
