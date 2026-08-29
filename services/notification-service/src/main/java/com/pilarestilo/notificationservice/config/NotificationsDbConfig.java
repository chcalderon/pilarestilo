package com.pilarestilo.notificationservice.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * The database this service owns: {@code pilarestilo_notifications}, read-write.
 *
 * <p>Hand-built because there is a second {@code EntityManagerFactory} in this service (the shared
 * read-only one in {@link ReadOnlyDbConfig}), and Boot's JPA auto-configuration backs off the
 * moment any {@code EntityManagerFactory} bean exists. This factory is {@code @Primary}.
 *
 * <p>Unlike the monolith, migrations run through a plain Flyway bean: there is no second migration
 * history to protect here, so the "not a Flyway bean" workaround is not needed. {@code @Bean}
 * declaring a {@code Flyway} makes {@code FlywayAutoConfiguration} back off.
 */
@Configuration
@EnableConfigurationProperties({ JpaProperties.class, HibernateProperties.class })
@EnableJpaRepositories(
        basePackages = "com.pilarestilo.notificationservice.infrastructure.persistence.owned",
        entityManagerFactoryRef = "notificationsEntityManagerFactory",
        transactionManagerRef = "notificationsTransactionManager")
public class NotificationsDbConfig {

    static final String PACKAGE = "com.pilarestilo.notificationservice.infrastructure.persistence.owned";

    @Bean
    @Primary
    @ConfigurationProperties("app.notification.datasource")
    DataSourceProperties notificationsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("app.notification.datasource.hikari")
    DataSource notificationsDataSource(
            @Qualifier("notificationsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(initMethod = "migrate")
    Flyway notificationsFlyway(@Qualifier("notificationsDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @Bean
    @Primary
    @DependsOn("notificationsFlyway")
    LocalContainerEntityManagerFactoryBean notificationsEntityManagerFactory(
            @Qualifier("notificationsDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {

        HibernateSettings settings = new HibernateSettings().ddlAuto(hibernateProperties::getDdlAuto);
        Map<String, Object> hibernateProps =
                hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), settings);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(vendorAdapter(jpaProperties));
        factory.setJpaPropertyMap(hibernateProps);
        factory.setPackagesToScan(PACKAGE);
        factory.setPersistenceUnitName("notifications");
        return factory;
    }

    @Bean
    @Primary
    PlatformTransactionManager notificationsTransactionManager(
            @Qualifier("notificationsEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /** Shared with the read-only factory so both databases speak to Hibernate the same way. */
    static HibernateJpaVendorAdapter vendorAdapter(JpaProperties jpaProperties) {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(jpaProperties.isShowSql());
        adapter.setGenerateDdl(jpaProperties.isGenerateDdl());
        if (jpaProperties.getDatabasePlatform() != null) {
            adapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        }
        return adapter;
    }
}
