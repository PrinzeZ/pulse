package com.pulse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local-only configuration placeholder.
 * Scheduling is enabled once globally by PulseApplication.
 * Keeping a second @EnableScheduling here would register scheduled tasks twice.
 */
@Configuration
@Profile("local")
public class LocalSchedulingConfig {
}
