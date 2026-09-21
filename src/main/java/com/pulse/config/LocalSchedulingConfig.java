package com.pulse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("local")
@EnableScheduling
public class LocalSchedulingConfig {
}
