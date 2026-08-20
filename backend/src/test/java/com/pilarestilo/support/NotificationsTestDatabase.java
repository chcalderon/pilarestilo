package com.pilarestilo.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gives a test the second database the application now expects.
 *
 * <p>Every test that boots the context needs one: with two DataSources declared, a context whose
 * second one points nowhere does not start at all. So this is called from the same
 * {@code @DynamicPropertySource} that already points the first one at the container.
 *
 * <p>It is a second database inside the same container, not a second container. The boundary being
 * tested is the one Postgres draws between databases -- no join, no shared transaction -- and that
 * holds whether or not the two live in the same process. A second container would double an
 * already twelve-minute suite to prove nothing extra.
 *
 * <p>Each test class still gets its own container, and so its own pair of databases. Sharing one
 * across classes would be faster and would trade the isolation the suite currently has for
 * whatever order the classes happen to run in.
 */
public final class NotificationsTestDatabase {

    private static final String DATABASE = "notifications";

    private NotificationsTestDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry, PostgreSQLContainer postgres) {
        create(postgres);
        String url = "jdbc:postgresql://" + postgres.getHost()
                + ":" + postgres.getFirstMappedPort() + "/" + DATABASE;
        registry.add("app.notification.datasource.url", () -> url);
        registry.add("app.notification.datasource.username", postgres::getUsername);
        registry.add("app.notification.datasource.password", postgres::getPassword);
    }

    private static void create(PostgreSQLContainer postgres) {
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DATABASE);
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Could not create the notifications test database in " + postgres.getJdbcUrl(), ex);
        }
    }
}
