package com.pilarestilo.order.infrastructure;

import com.pilarestilo.order.domain.model.OrderReference;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order reference is computed in two languages: Java mints it for new orders, and V67's
 * backfill wrote it in SQL for existing ones. Nothing in the type system ties them together, so
 * they would drift apart on the first refactor and nobody would notice until a customer quoted a
 * code that matched no order.
 *
 * <p>This asserts they agree byte for byte, including the salted form the migration's duplicate
 * repair loop uses.
 */
@Testcontainers
class OrderReferenceSqlParityIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    private String sqlReference(Statement statement, UUID id, Integer salt) throws Exception {
        String expr = salt == null
                ? "'PE-' || UPPER(SUBSTR(MD5('" + id + "'), 1, 10))"
                : "'PE-' || UPPER(SUBSTR(MD5('" + id + "' || '#' || " + salt + "), 1, 10))";
        try (ResultSet rs = statement.executeQuery("SELECT " + expr)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void javaAndSqlAgree() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {

            // A fixed id first, so a failure names the exact input rather than a random one.
            UUID pinned = UUID.fromString("3f9a2c71-b4d5-4e6f-8a9b-0c1d2e3f4a5b");
            assertThat(OrderReference.forOrderId(pinned)).isEqualTo(sqlReference(statement, pinned, null));

            for (int i = 0; i < 50; i++) {
                UUID id = UUID.randomUUID();
                assertThat(OrderReference.forOrderId(id))
                        .as("unsalted reference for %s", id)
                        .isEqualTo(sqlReference(statement, id, null));
            }
        }
    }

    @Test
    void javaAndSqlAgreeOnTheSaltedForm() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {

            for (int salt = 1; salt <= 5; salt++) {
                UUID id = UUID.randomUUID();
                assertThat(OrderReference.forOrderId(id, salt))
                        .as("salt %d for %s", salt, id)
                        .isEqualTo(sqlReference(statement, id, salt));
            }
        }
    }
}
