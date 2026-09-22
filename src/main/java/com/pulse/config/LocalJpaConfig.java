package com.pulse.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@Profile("local")
@EnableJpaRepositories(
        basePackages = "com.pulse.local.repository",
        entityManagerFactoryRef = "localEntityManagerFactory",
        transactionManagerRef = "localTransactionManager"
)
public class LocalJpaConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean localEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("localDataSource") DataSource dataSource) {

        return builder
                .dataSource(dataSource)
                .packages("com.pulse.local.model")
                .persistenceUnit("local")
                .properties(Map.of(
                        "hibernate.hbm2ddl.auto", "update"
                ))
                .build();
    }

    @Bean
    public PlatformTransactionManager localTransactionManager(
            @Qualifier("localEntityManagerFactory")
            EntityManagerFactory entityManagerFactory) {

        return new JpaTransactionManager(entityManagerFactory);
    }
}