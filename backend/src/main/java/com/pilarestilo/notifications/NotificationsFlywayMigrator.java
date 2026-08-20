package com.pilarestilo.notifications;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;

/**
 * Runs the notifications migrations, and is pointedly not a {@link Flyway} bean.
 *
 * <p>FlywayAutoConfiguration backs off on a bean of type {@code Flyway}. Declaring one here to
 * migrate the second database would therefore stop the first one from being migrated at all --
 * silently, since nothing announces an auto-configuration that decided not to run. Under
 * {@code ddl-auto: validate} the backend would simply fail to start against a schema nobody
 * updated, and the cause would be nowhere near the change that caused it.
 *
 * <p>So the migration is run by hand, by something Boot's condition does not recognise. The cost
 * is that {@code spring.flyway.*} does not reach it: this database's settings are its own, and
 * there are only two of them.
 */
public class NotificationsFlywayMigrator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(NotificationsFlywayMigrator.class);

    private final DataSource dataSource;
    private final String locations;

    public NotificationsFlywayMigrator(DataSource dataSource, String locations) {
        this.dataSource = dataSource;
        this.locations = locations;
    }

    @Override
    public void afterPropertiesSet() {
        int applied = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .load()
                .migrate()
                .migrationsExecuted;
        log.info("Notifications database migrated from {} ({} applied)", locations, applied);
    }
}
