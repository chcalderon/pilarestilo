package com.pilarestilo.product.infrastructure.persistence.entities;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingOriginZoneMigrationScriptsTest {

    @Test
    void runtimeAlignmentRepairPrereqPreservesPublishedLegacyNationalValue() throws IOException {
        String sql = readMigration("db/migration/V60_2__repair_retail_runtime_alignment_prereqs.sql");

        assertTrue(sql.contains("'NATIONAL'"));
        assertFalse(sql.contains("'NACIONAL'"));
    }

    @Test
    void repairMigrationNormalizesLegacyNationalRows() throws IOException {
        String sql = readMigration("db/migration/V66__repair_shipping_origin_zone_national_alias.sql");

        assertTrue(sql.contains("UPDATE products"));
        assertTrue(sql.contains("shipping_origin_zone = 'NACIONAL'"));
        assertTrue(sql.contains("shipping_origin_zone = 'NATIONAL'"));
    }

    private String readMigration(String path) throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Classpath resource not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
