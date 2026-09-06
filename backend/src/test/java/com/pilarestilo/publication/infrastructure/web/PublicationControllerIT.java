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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
// This class logs in once per test and sits near the per-IP auth rate limit (default 12);
// lift it so adding a test never flakes an unrelated one with a 429 on /auth/login.
@org.springframework.test.context.TestPropertySource(properties = "app.gateway.rate-limit.login-max-requests=500")
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

    @Autowired
    com.pilarestilo.publication.application.usecases.DispatchDuePublicationsUseCase dispatchDuePublicationsUseCase;

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

    @Test
    void admin_sees_a_published_batch_in_the_history() throws Exception {
        String adminToken = loginAdmin();
        Product product = productRepository.save(Product.create("Falda historial", "desc",
                new Money(BigDecimal.valueOf(29990), "CLP"), "https://cdn.example.com/falda.jpg",
                ProductCondition.NEW, "Pilar", 3));

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM", "FACEBOOK"),
                                "captionTemplate", "{producto} a solo {precio}",
                                "hashtags", List.of("#pilarestilo"),
                                "campaignLabel", "Historial Test"))))
                .andExpect(status().isOk());

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].campaignLabel").value("Historial Test"))
                .andExpect(jsonPath("$[0].total").value(2))
                .andExpect(jsonPath("$[0].failed").value(2))
                .andExpect(jsonPath("$[0].published").value(0))
                .andReturn();

        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        mvc.perform(get("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captionTemplate").value("{producto} a solo {precio}"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].status").value("FAILED"))
                .andExpect(jsonPath("$.rows[0].lastErrorCode").exists())
                .andExpect(jsonPath("$.productIds", hasItem(product.getId().toString())));
    }

    @Test
    void batch_detail_rows_carry_the_full_image_list() throws Exception {
        String adminToken = loginAdmin();
        Product product = productRepository.save(Product.create("Falda carrusel", "desc",
                new Money(BigDecimal.valueOf(29990), "CLP"), "https://cdn.example.com/cover.jpg",
                ProductCondition.NEW, "Pilar", 3));

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "imageSelections", Map.of(product.getId().toString(),
                                        List.of("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"))))))
                .andExpect(status().isOk());

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        mvc.perform(get("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].imageUrls[0]").value("https://cdn.example.com/a.jpg"))
                .andExpect(jsonPath("$.rows[0].imageUrls[1]").value("https://cdn.example.com/b.jpg"));
    }

    @Test
    void campaigns_list_groups_a_published_batch_by_its_label() throws Exception {
        // Generated, not a /auth/login call — the class already logs in enough times to sit near
        // the per-IP auth rate limit; one more real login flakes an unrelated later test with 429.
        String adminToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "admin@pilarestilo.com", UserRole.ADMIN, List.of(), List.of());
        Product product = productRepository.save(Product.create("Falda campaña", "d",
                new Money(BigDecimal.valueOf(29990), "CLP"), "https://cdn.example.com/f.jpg",
                ProductCondition.NEW, "Pilar", 3));

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "campaignLabel", "Campaña de prueba"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/publications/campaigns").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.label == 'Campaña de prueba')]").exists());

        mvc.perform(get("/api/admin/publications/campaigns/detail")
                        .param("label", "Campaña de prueba")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Campaña de prueba"))
                .andExpect(jsonPath("$.posts", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void refresh_metrics_requires_update_permission() throws Exception {
        String sellerToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "seller-metrics@pilarestilo.com",
                UserRole.SELLER,
                List.of("productos"),
                List.of("publications.read"));

        mvc.perform(post("/api/admin/publications/campaigns/refresh-metrics")
                        .param("label", "x")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void retry_failed_in_batch_reschedules_the_failed_rows_for_the_worker() throws Exception {
        String adminToken = loginAdmin();
        Product product = productRepository.save(Product.create("Blusa retry", "desc",
                new Money(BigDecimal.valueOf(19990), "CLP"), "https://cdn.example.com/blusa.jpg",
                ProductCondition.NEW, "Pilar", 2));

        MvcResult batchResult = mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}"))))
                .andExpect(status().isOk())
                .andReturn();
        String publicationId = om.readTree(batchResult.getResponse().getContentAsString())
                .get("items").get(0).get("publicationId").asString();

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        mvc.perform(post("/api/admin/publications/batches/{id}/retry-failed", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].status").value("RETRY_SCHEDULED"));

        mvc.perform(get("/api/admin/publications/{id}", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY_SCHEDULED"))
                .andExpect(jsonPath("$.retryCount").value(0));
    }

    @Test
    void retry_failed_in_batch_requires_update_permission() throws Exception {
        String sellerToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "seller-retry@pilarestilo.com", UserRole.SELLER,
                List.of("productos"), List.of("publications.read"));

        mvc.perform(post("/api/admin/publications/batches/{id}/retry-failed", UUID.randomUUID())
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknown_batch_id_returns_404() throws Exception {
        String adminToken = loginAdmin();
        mvc.perform(get("/api/admin/publications/batches/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void a_scheduled_batch_shows_as_scheduled_and_can_be_cancelled_rescheduled_and_edited() throws Exception {
        String adminToken = loginAdmin();
        Product p1 = productRepository.save(Product.create("Falda prog", "d",
                new Money(BigDecimal.valueOf(29990), "CLP"), "https://cdn.example.com/f.jpg",
                ProductCondition.NEW, "Pilar", 3));
        String future = java.time.Instant.now().plusSeconds(3 * 3600).toString();

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p1.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "campaignLabel", "Programada Test",
                                "scheduledAt", future))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].scheduled").value(true))
                .andExpect(jsonPath("$.items[0].success").value(false));

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduledAt").exists())
                .andReturn();
        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        mvc.perform(get("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.rows[0].status").value("SCHEDULED"));

        String later = java.time.Instant.now().plusSeconds(5 * 3600).toString();
        mvc.perform(post("/api/admin/publications/batches/{id}/reschedule", batchId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("scheduledAt", later))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledAt", org.hamcrest.Matchers.startsWith(later.substring(0, 19))));

        mvc.perform(post("/api/admin/publications/batches/{id}/reschedule", batchId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("scheduledAt",
                                java.time.Instant.now().minusSeconds(60).toString()))))
                .andExpect(status().isBadRequest());

        Product p2 = productRepository.save(Product.create("Blusa prog", "d",
                new Money(BigDecimal.valueOf(19990), "CLP"), "https://cdn.example.com/b.jpg",
                ProductCondition.NEW, "Pilar", 2));
        mvc.perform(put("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p1.getId().toString(), p2.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "Nuevo {producto}",
                                "scheduledAt", later))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captionTemplate").value("Nuevo {producto}"))
                .andExpect(jsonPath("$.rows", hasSize(2)));

        mvc.perform(post("/api/admin/publications/batches/{id}/cancel", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].status").value("CANCELLED"));
    }

    @Test
    void a_batch_scheduled_in_the_past_is_rejected() throws Exception {
        String adminToken = loginAdmin();
        Product p = productRepository.save(Product.create("Prod pasado", "d",
                new Money(BigDecimal.valueOf(9990), "CLP"), "https://cdn.example.com/p.jpg",
                ProductCondition.NEW, "Pilar", 1));
        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "scheduledAt", java.time.Instant.now().minusSeconds(60).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void the_scheduled_job_publishes_a_due_batch() throws Exception {
        String adminToken = loginAdmin();
        Product p = productRepository.save(Product.create("Prod due", "d",
                new Money(BigDecimal.valueOf(9990), "CLP"), "https://cdn.example.com/d.jpg",
                ProductCondition.NEW, "Pilar", 1));
        String soon = java.time.Instant.now().plusSeconds(1).toString();
        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "scheduledAt", soon))))
                .andExpect(status().isOk());

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken))).andReturn();
        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        Thread.sleep(1400);
        int handled = dispatchDuePublicationsUseCase.execute();
        org.junit.jupiter.api.Assertions.assertTrue(handled >= 1);

        mvc.perform(get("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.rows[0].status").value("FAILED"));
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
