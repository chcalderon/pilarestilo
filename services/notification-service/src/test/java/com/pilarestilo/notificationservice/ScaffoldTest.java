package com.pilarestilo.notificationservice;

import com.pilarestilo.notificationservice.auth.JwtAuthenticationFilter;
import com.pilarestilo.notificationservice.auth.JwtTokenProvider;
import com.pilarestilo.notificationservice.config.SecurityConfig;
import com.pilarestilo.notificationservice.infrastructure.web.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web scaffold: the health ping is the one route reachable without a token; everything else
 * under {@code /api/notifications} is authenticated. Full-context wiring (both datasources, Flyway)
 * is asserted by {@link NotificationStoreIT} against a real Postgres.
 */
@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class })
@TestPropertySource(properties = "app.jwt.secret=U2VjcmV0U2VjcmV0MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=")
class ScaffoldTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void health_is_open() throws Exception {
        mockMvc.perform(get("/api/notifications/_health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void bell_reads_require_a_token() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is(oneOf(401, 403)));
    }
}
