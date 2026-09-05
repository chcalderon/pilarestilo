package com.pilarestilo.publication.infrastructure.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
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

import java.math.BigDecimal;
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
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    ProductRepository productRepository;

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
    void admin_can_approve_and_dispatch_a_publication_synchronously() throws Exception {
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
                // No Meta credentials are configured in this test environment, so the outbound
                // call itself fails — but that failure must actually reach the database, which is
                // exactly the rollback bug this cutover fixed. Before the fix this row would have
                // stayed at PUBLISHING forever with no error recorded.
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts", hasSize(1)))
                .andExpect(jsonPath("$.lastErrorCode").exists());

        mvc.perform(get("/api/admin/publications/{id}", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void batch_endpoint_requires_publications_update_permission() throws Exception {
        String sellerToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "seller-batch@pilarestilo.com",
                UserRole.SELLER,
                List.of("productos"),
                List.of("publications.read")
        );

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(UUID.randomUUID().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void batch_publishes_each_product_times_platform_combination_and_survives_a_missing_product() throws Exception {
        String adminToken = loginAdmin();
        Product product = Product.create("Chaqueta boutique", "desc",
                new Money(BigDecimal.valueOf(49990), "CLP"), "https://cdn.example.com/chaqueta.jpg",
                ProductCondition.NEW, "Pilar", 5);
        Product saved = productRepository.save(product);
        String missingProductId = UUID.randomUUID().toString();

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(saved.getId().toString(), missingProductId),
                                "platforms", List.of("INSTAGRAM", "FACEBOOK"),
                                "captionTemplate", "{producto} a solo {precio}",
                                "hashtags", List.of("#pilarestilo"),
                                "campaignLabel", "Liquidacion"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].productId").value(saved.getId().toString()))
                .andExpect(jsonPath("$.items[0].platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.items[0].publicationId").exists())
                // No Meta credentials configured in this test env: the outbound call itself fails,
                // proving the dispatch path really ran rather than being skipped.
                .andExpect(jsonPath("$.items[0].success").value(false))
                .andExpect(jsonPath("$.items[2].productId").value(missingProductId))
                .andExpect(jsonPath("$.items[2].publicationId").doesNotExist())
                .andExpect(jsonPath("$.items[2].errorMessage", org.hamcrest.Matchers.containsString("no encontrado")));
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
