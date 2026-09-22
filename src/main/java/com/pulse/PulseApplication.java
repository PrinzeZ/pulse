package com.pulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import com.pulse.config.Phase3AdminInitializer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.pulse.repository.UserRepository;
import com.pulse.local.service.LocalOfflineStore;
import org.springframework.beans.factory.ObjectProvider;

@SpringBootApplication
@EnableScheduling
public class PulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseApplication.class, args);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Profile("dev")
    @Bean
    @Order(1)
    public CommandLineRunner phase3AdminInitializer(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, ObjectProvider<LocalOfflineStore> localStoreProvider) {
        return new Phase3AdminInitializer(userRepository, passwordEncoder, localStoreProvider);
    }
}