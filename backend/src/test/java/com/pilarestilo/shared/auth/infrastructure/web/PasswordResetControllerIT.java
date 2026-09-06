package com.pilarestilo.shared.auth.infrastructure.web;

import com.pilarestilo.shared.auth.domain.ports.PasswordResetMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PasswordResetControllerIT {

    private static final String SAME_BODY =
            "Si el correo existe, te enviamos un enlace para restablecer tu contraseña.";

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // The rate limiter is per-IP and process-wide; several forgot-password calls in one test
        // would trip it. Off for this class.
        r.add("app.gateway.rate-limit.enabled", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean PasswordResetMailer mailer;

    private String register(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", email, "password", password, "fullName", "Test Person",
                                "acceptsMarketing", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        return result.getResponse().getStatus() == 200
                ? om.readTree(result.getResponse().getContentAsString()).get("accessToken").asString()
                : null;
    }

    @Test
    void forgot_password_returns_the_same_200_body_for_known_and_unknown_emails() throws Exception {
        register("known-" + UUID.randomUUID() + "@example.com", "Password123");

        for (String email : new String[]{"nobody-" + UUID.randomUUID() + "@example.com"}) {
            mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(SAME_BODY));
        }
    }

    @Test
    void the_full_flow_changes_the_password_and_kills_the_old_session() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        String body = register(email, "OldPassword1");
        String oldAccessToken = om.readTree(body).get("accessToken").asString();

        mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq(email), any(), rawToken.capture());

        mvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "token", rawToken.getValue(), "newPassword", "BrandNewPass1"))))
                .andExpect(status().isNoContent());

        // Old access token is now behind the user's session_version: the filter leaves the request
        // anonymous, and this app answers a rejected token on a guarded route with 403 (same as an
        // expired one — there is no 401 entry point).
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isForbidden());
        // The old password no longer logs in; the new one does.
        org.junit.jupiter.api.Assertions.assertNull(login(email, "OldPassword1"));
        org.junit.jupiter.api.Assertions.assertNotNull(login(email, "BrandNewPass1"));
    }

    @Test
    void reset_password_with_a_garbage_token_is_a_generic_400() throws Exception {
        mvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "token", "not-a-real-token", "newPassword", "BrandNewPass1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace no es válido o ya expiró"));
    }

    @Test
    void requesting_a_second_link_invalidates_the_first() throws Exception {
        String email = "reissue-" + UUID.randomUUID() + "@example.com";
        register(email, "OldPassword1");

        mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", email)))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", email)))).andExpect(status().isOk());

        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(2)).sendResetLink(eq(email), any(), tokens.capture());
        String firstToken = tokens.getAllValues().get(0);

        mvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "token", firstToken, "newPassword", "BrandNewPass1"))))
                .andExpect(status().isBadRequest());
    }
}
