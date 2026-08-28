package com.pilarestilo.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The scaffold: the context starts, the security chain is wired, and the health ping is the one
 * route reachable without a token. The persistence layers land in later tasks, so their
 * auto-configuration is excluded here.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class ScaffoldTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void context_loads() {
        // The @SpringBootTest bootstrap is the assertion.
    }

    @Test
    void health_is_open() throws Exception {
        mockMvc.perform(get("/api/notifications/_health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void bell_reads_require_a_token() throws Exception {
        // Spring Security answers an unauthenticated REST request with 403 by default (no
        // AuthenticationEntryPoint configured), same as the other extracted services.
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is(oneOf(401, 403)));
    }
}
