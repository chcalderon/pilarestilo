package com.pilarestilo.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
class FlywayPublishedMigrationCompatibilityIT {

    private static final Pattern WHOLE_NUMBER_MIGRATION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @TempDir
    Path tempDir;

    @Test
    void current_migrations_upgrade_a_database_that_already_applied_the_original_v61() throws Exception {
        Path baselineMigrationDir = Files.createDirectory(tempDir.resolve("baseline-migrations"));
        Path currentMigrationDir = resolveClasspathDirectory("db/migration");

        copyPublishedMigrationsThroughV60(currentMigrationDir, baselineMigrationDir);

        migrate("filesystem:" + baselineMigrationDir.toAbsolutePath());
        seedLegacyArosCategory();
        copyLegacyV61(baselineMigrationDir);
        migrate("filesystem:" + baselineMigrationDir.toAbsolutePath());

        Flyway currentFlyway = migrate("classpath:db/migration");

        assertNotNull(currentFlyway.info().current());
        assertEquals("64", currentFlyway.info().current().getVersion().getVersion());
        assertTrue(Stream.of(currentFlyway.info().all())
                .anyMatch(info -> info.getVersion() != null && "60.1".equals(info.getVersion().getVersion())));
    }

    private void copyPublishedMigrationsThroughV60(Path sourceDir, Path targetDir) throws IOException {
        try (Stream<Path> paths = Files.list(sourceDir)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isWholeNumberMigration(path.getFileName().toString()))
                    .filter(path -> migrationVersion(path.getFileName().toString()) <= 60)
                    .sorted(Comparator.comparingInt(path -> migrationVersion(path.getFileName().toString())))
                    .toList();
            for (Path file : files) {
                Files.copy(file, targetDir.resolve(file.getFileName()));
            }
        }
    }

    private void copyLegacyV61(Path targetDir) throws IOException, URISyntaxException {
        Path legacyV61 = resolveClasspathFile("db/migration-legacy/V61__retail_runtime_alignment.sql");
        Files.copy(legacyV61, targetDir.resolve("V61__retail_runtime_alignment.sql"));
    }

    private void seedLegacyArosCategory() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO categories (
                        id,
                        slug,
                        name_es,
                        name_en,
                        parent_id,
                        sort_order,
                        active,
                        image_url
                    ) VALUES (
                        '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99',
                        'aros',
                        'Aros',
                        'Earrings',
                        '30000000-0000-0000-0000-000000000008',
                        1,
                        TRUE,
                        'https://images.unsplash.com/photo-1617038220319-276d3cfab638?w=900&q=80'
                    )
                    ON CONFLICT (id) DO NOTHING
                    """);
        }
    }

    private Flyway migrate(String location) {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(location)
                .outOfOrder(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    private static int migrationVersion(String filename) {
        Matcher matcher = WHOLE_NUMBER_MIGRATION.matcher(filename);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected migration filename: " + filename);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean isWholeNumberMigration(String filename) {
        return WHOLE_NUMBER_MIGRATION.matcher(filename).matches();
    }

    private static Path resolveClasspathDirectory(String location) throws URISyntaxException {
        URL url = Thread.currentThread().getContextClassLoader().getResource(location);
        if (url == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + location);
        }
        return Path.of(url.toURI());
    }

    private static Path resolveClasspathFile(String location) throws URISyntaxException {
        URL url = Thread.currentThread().getContextClassLoader().getResource(location);
        if (url == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + location);
        }
        return Path.of(url.toURI());
    }
}
