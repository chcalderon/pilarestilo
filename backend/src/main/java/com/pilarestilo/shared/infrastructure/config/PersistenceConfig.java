package com.pilarestilo.shared.infrastructure.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * The main database, wired by hand.
 *
 * <p>Boot builds all of this itself, and did until now. What forces the change is that
 * notifications are moving to a database of their own: a second EntityManagerFactory has to be a
 * bean for {@code @EnableJpaRepositories} to point at it, and the moment any EntityManagerFactory
 * bean exists, HibernateJpaAutoConfiguration backs off and stops building the main one. The
 * DataSource goes the same way for the same reason. So both sides are declared here rather than
 * one being auto-configured and the other not, which would read as an accident.
 *
 * <p>Flyway is deliberately <strong>not</strong> here. FlywayAutoConfiguration backs off on a bean
 * of type {@code Flyway}, so declaring one for notifications would quietly stop the main
 * migrations from running -- with {@code ddl-auto: validate} that is a backend that will not come
 * up, and it is the failure this codebase has already been bitten by in other guises. The
 * notifications migrations run through a bean that is not a {@code Flyway}, which leaves the main
 * auto-configuration untouched and doing its job.
 *
 * <p>The packages come from {@link EntityScanConfig} through {@link EntityScanPackages} rather
 * than being repeated here, so the annotation stays the one list, and the one thing the guard test
 * reads.
 */
@Configuration
@EnableConfigurationProperties({ JpaProperties.class, HibernateProperties.class })
@EnableJpaRepositories(
        basePackages = "com.pilarestilo",
        /*
         * Repositories are scanned from the root; only the notifications ones are carved out. The
         * exclusion is what keeps NotificationJpaRepository off this factory -- without it the
         * repository binds here, to the database that no longer holds its table.
         */
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.pilarestilo\\.notifications\\..*"),
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class PersistenceConfig {

    /*
     * @Primary is doing real work here, not decoration. DataSourceAutoConfiguration registers a
     * DataSourceProperties of its own through @EnableConfigurationProperties whatever else is
     * declared, so from the moment notifications add a third there are several in the context and
     * an unqualified injection has nothing to choose by. Marking this one primary and naming the
     * other explicitly is what keeps each database reading its own settings -- getting that wrong
     * does not fail, it points a factory at the wrong database.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource dataSource(@Qualifier("dataSourceProperties") DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    LocalContainerEntityManagerFactoryBean entityManagerFactory(@Qualifier("dataSource") DataSource dataSource,
                                                                JpaProperties jpaProperties,
                                                                HibernateProperties hibernateProperties,
                                                                BeanFactory beanFactory) {
        /*
         * ddlAuto is handed over explicitly instead of being left to the fallback inside
         * determineHibernateProperties. If it ever resolved to none, every mapping check would go
         * away and nothing would fail: the tables are already there, put there by Flyway. A
         * safety net that stops working in silence is worse than none.
         */
        HibernateSettings settings = new HibernateSettings().ddlAuto(hibernateProperties::getDdlAuto);
        Map<String, Object> hibernateProps =
                hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), settings);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(vendorAdapter(jpaProperties));
        factory.setJpaPropertyMap(hibernateProps);
        factory.setPackagesToScan(
                EntityScanPackages.get(beanFactory).getPackageNames().toArray(String[]::new));
        factory.setPersistenceUnitName("default");
        return factory;
    }

    @Bean
    @Primary
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /** Shared with the notifications factory so both databases speak to Hibernate the same way. */
    public static HibernateJpaVendorAdapter vendorAdapter(JpaProperties jpaProperties) {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(jpaProperties.isShowSql());
        adapter.setGenerateDdl(jpaProperties.isGenerateDdl());
        if (jpaProperties.getDatabasePlatform() != null) {
            adapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        }
        return adapter;
    }
}
