package com.pilarestilo.notificationservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * A read-only view of the shared {@code pilarestilo} database — exactly how the other four extracted
 * services reach it. Maps only the columns the dispatchers and {@code NotificationComposer} render;
 * {@code ddl-auto: validate} against the real schema is the guard that turns a monolith column
 * rename into a failed boot here (and, via {@code ReadOnlyMappingIT}, a red local test).
 *
 * <p>Not {@code @Primary}: the owned notifications database in {@link NotificationsDbConfig} is.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.pilarestilo.notificationservice.infrastructure.persistence.readonly",
        entityManagerFactoryRef = "sharedRoEntityManagerFactory",
        transactionManagerRef = "sharedRoTransactionManager")
public class ReadOnlyDbConfig {

    static final String PACKAGE = "com.pilarestilo.notificationservice.infrastructure.persistence.readonly";

    @Bean
    @ConfigurationProperties("app.shared-db.datasource")
    DataSourceProperties sharedRoDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.shared-db.datasource.hikari")
    DataSource sharedRoDataSource(
            @Qualifier("sharedRoDataSourceProperties") DataSourceProperties properties) {
        DataSource dataSource = properties.initializeDataSourceBuilder().build();
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
            hikari.setReadOnly(true);
            hikari.setPoolName("shared-ro");
        }
        return dataSource;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean sharedRoEntityManagerFactory(
            @Qualifier("sharedRoDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {

        HibernateSettings settings = new HibernateSettings().ddlAuto(hibernateProperties::getDdlAuto);
        Map<String, Object> hibernateProps =
                hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), settings);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(NotificationsDbConfig.vendorAdapter(jpaProperties));
        factory.setJpaPropertyMap(hibernateProps);
        factory.setPackagesToScan(PACKAGE);
        factory.setPersistenceUnitName("shared-ro");
        return factory;
    }

    @Bean
    PlatformTransactionManager sharedRoTransactionManager(
            @Qualifier("sharedRoEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
