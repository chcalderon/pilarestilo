package com.pilarestilo.notifications;

import com.pilarestilo.shared.infrastructure.config.PersistenceConfig;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * The notifications database: its own DataSource, its own EntityManagerFactory, its own migration
 * history.
 *
 * <p>The point of the exercise is that this is a boundary the engine enforces rather than one
 * people remember. With the table in another database Postgres will not join it to anything, so
 * nobody can cross the line by accident or in a hurry, and no future screen can quietly grow a
 * dependency that has to be unpicked later.
 *
 * <p>What it costs is atomicity. Nothing can be written here and in the main database under one
 * transaction any more. Today nothing tries -- no transaction writes notifications and another
 * table together -- which is exactly why this module went first.
 *
 * <p>Every {@code @Transactional} that reaches this side has to name {@link #TRANSACTION_MANAGER}.
 * Left unqualified it gets the main one, which governs the other database, and the write lands
 * outside the transaction that was supposed to hold it. In this module that failure is quiet:
 * {@code InAppNotificationSender} logs and swallows, so the notification would simply not exist.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = NotificationsPersistenceConfig.ROOT_PACKAGE,
        entityManagerFactoryRef = "notificationsEntityManagerFactory",
        transactionManagerRef = NotificationsPersistenceConfig.TRANSACTION_MANAGER)
public class NotificationsPersistenceConfig {

    /** The one package this factory maps, and the one the main factory must not. */
    public static final String ROOT_PACKAGE = "com.pilarestilo.notifications";

    /** Named by every {@code @Transactional} that touches this database. */
    public static final String TRANSACTION_MANAGER = "notificationsTransactionManager";

    @Bean
    @ConfigurationProperties("app.notification.datasource")
    DataSourceProperties notificationsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.notification.datasource.hikari")
    DataSource notificationsDataSource(
            @Qualifier("notificationsDataSourceProperties") DataSourceProperties notificationsDataSourceProperties) {
        return notificationsDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    NotificationsFlywayMigrator notificationsFlywayMigrator(
            @Qualifier("notificationsDataSource") DataSource notificationsDataSource,
            @Value("${app.notification.flyway.locations}") String locations) {
        return new NotificationsFlywayMigrator(notificationsDataSource, locations);
    }

    /**
     * Depends on the migrator by name rather than by argument, because the schema has to exist
     * before this factory validates against it and Spring has no other reason to order the two.
     */
    @Bean
    @DependsOn("notificationsFlywayMigrator")
    LocalContainerEntityManagerFactoryBean notificationsEntityManagerFactory(
            @Qualifier("notificationsDataSource") DataSource notificationsDataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {

        HibernateSettings settings = new HibernateSettings().ddlAuto(hibernateProperties::getDdlAuto);
        Map<String, Object> hibernateProps =
                hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), settings);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(notificationsDataSource);
        factory.setJpaVendorAdapter(PersistenceConfig.vendorAdapter(jpaProperties));
        factory.setJpaPropertyMap(hibernateProps);
        factory.setPackagesToScan(ROOT_PACKAGE);
        factory.setPersistenceUnitName("notifications");
        return factory;
    }

    @Bean(TRANSACTION_MANAGER)
    PlatformTransactionManager notificationsTransactionManager(
            @Qualifier("notificationsEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
