package com.pilarestilo.notificationservice.support;

import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One Postgres container, started once for the whole JVM (the Testcontainers singleton pattern —
 * no {@code @Container}/{@code @Testcontainers}, Ryuk reaps it at exit), holding both databases:
 * <ul>
 *   <li>{@code pilarestilo_notifications} — owned, migrated by the app's own Flyway bean;</li>
 *   <li>{@code pilarestilo} — the shared database, migrated here with the <b>monolith's real
 *       migration set</b> so the read-only entities validate against the schema they meet in
 *       production. This is the drift guard: a monolith column rename not mirrored in a
 *       {@code *RoEntity} fails this boot.</li>
 * </ul>
 */
public abstract class AbstractSharedStackIT {

    private static final String NOTIFICATIONS_DB = "pilarestilo_notifications";
    private static final String MONOLITH_MIGRATIONS =
            "filesystem:../../backend/src/main/resources/db/migration";

    @SuppressWarnings("resource") // singleton for the whole JVM — Ryuk reaps it at exit, closing it
                                  // here would restart a container per test class (see class doc)
    protected static final PostgreSQLContainer POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer("postgres:16")
                .withDatabaseName("pilarestilo")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
        createNotificationsDatabase();
        migrateSharedSchema();
    }

    @DynamicPropertySource
    static void datasources(DynamicPropertyRegistry registry) {
        registry.add("app.shared-db.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("app.shared-db.datasource.username", POSTGRES::getUsername);
        registry.add("app.shared-db.datasource.password", POSTGRES::getPassword);

        registry.add("app.notification.datasource.url", AbstractSharedStackIT::notificationsJdbcUrl);
        registry.add("app.notification.datasource.username", POSTGRES::getUsername);
        registry.add("app.notification.datasource.password", POSTGRES::getPassword);
    }

    protected static String notificationsJdbcUrl() {
        return POSTGRES.getJdbcUrl().replace("/pilarestilo", "/" + NOTIFICATIONS_DB);
    }

    private static void createNotificationsDatabase() {
        try (Connection c = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + NOTIFICATIONS_DB);
        } catch (SQLException ex) {
            if (!ex.getMessage().contains("already exists")) {
                throw new IllegalStateException("Could not create " + NOTIFICATIONS_DB, ex);
            }
        }
    }

    private static void migrateSharedSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(MONOLITH_MIGRATIONS)
                .load()
                .migrate();
    }
}
