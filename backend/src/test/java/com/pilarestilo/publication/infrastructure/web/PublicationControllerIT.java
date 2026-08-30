package com.pilarestilo.publication.infrastructure.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PublicationControllerIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.social-publishing.n8n.callback-token", () -> "test-social-token");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Test
    void admin_can_create_list_and_retrieve_publication() throws Exception {
        String adminToken = loginAdmin();
        String idempotencyKey = "pub-" + UUID.randomUUID();

        MvcResult created = mvc.perform(post("/api/admin/publications")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "sourceType", "PRODUCT",
                                "platform", "INSTAGRAM",
                                "channelType", "FEED_POST",
                                "locale", "es-CL",
                                "campaignLabel", "Invierno Boutique",
                                "caption", "Look boutique aprobado",
                                "hashtags", List.of("#pilarestilo", "#invierno"),
                                "approvalRequired", true,
                                "idempotencyKey", idempotencyKey,
                                "mediaBundles", List.of(Map.of(
                                        "bundleType", "SOCIAL_FEED",
                                        "primaryAssetUrl", "https://cdn.example.com/social-feed.jpg",
                                        "assetManifest", Map.of(
                                                "targetAspectRatio", "4:5",
                                                "derivedAssetIds", List.of("asset-1")
                                        )
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.platform").value("INSTAGRAM"))
                .andReturn();

        JsonNode createdBody = om.readTree(created.getResponse().getContentAsString());
        String publicationId = createdBody.get("id").asString();

        mvc.perform(get("/api/admin/publications")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(publicationId)));

        mvc.perform(get("/api/admin/publications/{id}", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicationId))
                .andExpect(jsonPath("$.mediaBundles", hasSize(1)));
    }

    @Test
    void duplicate_idempotency_key_returns_existing_publication() throws Exception {
        String adminToken = loginAdmin();
        String idempotencyKey = "pub-" + UUID.randomUUID();
        String body = om.writeValueAsString(Map.of(
                "sourceType", "PRODUCT",
                "platform", "FACEBOOK",
                "channelType", "FEED_POST",
                "locale", "es-CL",
                "caption", "Primera version",
                "approvalRequired", true,
                "idempotencyKey", idempotencyKey
        ));

        MvcResult first = mvc.perform(post("/api/admin/publications")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = om.readTree(first.getResponse().getContentAsString()).get("id").asString();

        mvc.perform(post("/api/admin/publications")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void seller_can_read_but_cannot_mutate_publications() throws Exception {
        String adminToken = loginAdmin();
        String sellerToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "seller-view@pilarestilo.com",
                UserRole.SELLER,
                List.of("productos"),
                List.of("publications.read")
        );

        MvcResult created = mvc.perform(post("/api/admin/publications")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "sourceType", "MANUAL",
                                "platform", "INSTAGRAM",
                                "channelType", "FEED_POST",
                                "locale", "es-CL",
                                "caption", "Publicacion manual",
                                "approvalRequired", true,
                                "idempotencyKey", "pub-" + UUID.randomUUID()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String publicationId = om.readTree(created.getResponse().getContentAsString()).get("id").asString();

        mvc.perform(get("/api/admin/publications")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/publications/{id}", publicationId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/publications/{id}/submit-review", publicationId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_approve_dispatch_and_finalize_publication_via_callback() throws Exception {
        String adminToken = loginAdmin();

        MvcResult created = mvc.perform(post("/api/admin/publications")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "sourceType", "PRODUCT",
                                "platform", "INSTAGRAM",
                                "channelType", "FEED_POST",
                                "locale", "es-CL",
                                "caption", "Publicacion aprobable",
                                "approvalRequired", true,
                                "idempotencyKey", "pub-" + UUID.randomUUID()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String publicationId = om.readTree(created.getResponse().getContentAsString()).get("id").asString();

        mvc.perform(post("/api/admin/publications/{id}/submit-review", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mvc.perform(post("/api/admin/publications/{id}/approve", publicationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("comment", "Aprobado editorialmente"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(post("/api/admin/publications/{id}/dispatch", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHING"))
                .andExpect(jsonPath("$.attempts", hasSize(1)));

        mvc.perform(post("/api/publications/{id}/external-result", publicationId)
                        .header("X-PE-N8N-TOKEN", "test-social-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "workflowRunId", "wf-1",
                                "attemptNumber", 1,
                                "status", "SUCCEEDED",
                                "remotePostId", "178923456",
                                "publishedAt", "2026-05-18T05:20:00Z"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.remotePostId").value("178923456"));

        mvc.perform(get("/api/admin/publications/{id}", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.externalPostId").value("178923456"));
    }

    private String loginAdmin() throws Exception {
        return login("admin@pilarestilo.com", "admin2026");
    }

    private String login(String email, String password) throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
