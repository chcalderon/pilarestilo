package com.pilarestilo.systemsettings.infrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V97 repairs a wrong seed (LOCAL had 4 of the Aconcagua valley's 10 comunas; REGIONAL was named
 * "V Region y RM" but shipped with an empty comuna list, so nothing outside Aconcagua could ever
 * match a zone). This runs the real migration set against a real Postgres and reads back the
 * result -- a hand-typed JSON literal with 37 accented comuna names is exactly the kind of thing
 * that silently drops or misspells one entry, and only a real query catches that.
 */
@Testcontainers
@SpringBootTest
class ShippingZoneSeedRepairIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode zone(String code) {
        String json = jdbc.queryForObject(
                "SELECT shipping_zones_json FROM system_settings WHERE id = 1", String.class);
        JsonNode zones = MAPPER.readTree(json);
        for (JsonNode z : zones) {
            if (code.equals(z.get("code").asString())) return z;
        }
        throw new AssertionError("Zone not found: " + code);
    }

    private List<String> comunas(JsonNode zone) {
        List<String> out = new ArrayList<>();
        zone.get("comunas").forEach(n -> out.add(n.asString()));
        return out;
    }

    @Test
    void local_covers_all_ten_aconcagua_valley_comunas() {
        List<String> local = comunas(zone("LOCAL"));

        assertThat(local).containsExactlyInAnyOrder(
                "Los Andes", "San Esteban", "Calle Larga", "Rinconada",
                "San Felipe", "Putaendo", "Santa María", "Panquehue", "Llay-Llay", "Catemu"
        );
    }

    @Test
    void regional_covers_the_rest_of_valparaiso_region_only() {
        JsonNode regional = zone("REGIONAL");
        List<String> comunas = comunas(regional);

        // A sample across every province of the region except Los Andes/San Felipe (that's LOCAL).
        assertThat(comunas)
                .hasSize(27)
                .contains(
                        "Valparaíso", "Viña del Mar", "Quilpué", "Villa Alemana", "Limache", "Olmué",
                        "Quillota", "La Calera", "San Antonio", "Cartagena", "La Ligua", "Petorca",
                        "Casablanca", "Concón", "Quintero"
                );
        // Aconcagua's own comunas never appear in both zones at once.
        assertThat(comunas).doesNotContain("Los Andes", "San Felipe");
        // Isla de Pascua is administratively in this region but reached by plane, not a regular
        // courier -- deliberately left for NACIONAL rather than promising REGIONAL's ETA.
        assertThat(comunas).doesNotContain("Isla de Pascua");
        // No longer promises "y RM": Santiago/RM falls to NACIONAL along with the rest of Chile.
        assertThat(regional.get("titleEs").asString()).doesNotContain("RM");
        assertThat(regional.get("titleEn").asString()).doesNotContain("Metropolitan");
    }

    @Test
    void nacional_stays_the_implicit_everywhere_else_zone() {
        // No explicit list to keep in sync by hand -- the frontend treats "not LOCAL, not
        // REGIONAL" as NACIONAL, so a comuna the INE creates tomorrow is never silently unrouted.
        assertThat(comunas(zone("NACIONAL"))).isEmpty();
    }
}
