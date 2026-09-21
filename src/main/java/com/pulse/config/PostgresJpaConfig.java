package com.pulse.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.pulse.repository",
        entityManagerFactoryRef = "postgresEntityManagerFactory",
        transactionManagerRef = "postgresTransactionManager"
)
public class PostgresJpaConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource(
            @Qualifier("postgresDataSourceProperties") DataSourceProperties properties) {
        DataSource dataSource = properties.initializeDataSourceBuilder().build();
        if (dataSource instanceof HikariDataSource hikari) {
            // PostgreSQL is optional in local/offline mode. Do not make startup depend
            // on an Internet connection, and reconnect quickly when connectivity returns.
            hikari.setInitializationFailTimeout(-1);
            hikari.setConnectionTimeout(3000);
            hikari.setValidationTimeout(1000);
            hikari.setMaxLifetime(240000);
        }
        return dataSource;
    }

    @Bean(name = "postgresEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource dataSource,
            @Value("${pulse.cloud.ddl-auto:update}") String ddlAuto) {

        return builder
                .dataSource(dataSource)
                .packages("com.pulse.model")
                .persistenceUnit("postgres")
                .properties(Map.of(
                        "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                        "hibernate.hbm2ddl.auto", ddlAuto,
                        "hibernate.show_sql", "true",
                        "hibernate.boot.allow_jdbc_metadata_access", "false"
                ))
                .build();
    }

    @Bean(name = "postgresTransactionManager")
    @Primary
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory")
            EntityManagerFactory entityManagerFactory) {

        return new JpaTransactionManager(entityManagerFactory);
    }
}
