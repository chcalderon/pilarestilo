package com.pilarestilo.publication.infrastructure.web;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.application.usecases.DispatchDuePublicationsUseCase;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.user.domain.enums.UserRole;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.gateway.rate-limit.login-max-requests=500",
        "app.social-publishing.dispatch.backoff-minutes=0,0,0,0,0",
        // Disable the background worker: the tests drive worker.execute() explicitly and assert
        // intermediate states, so an every-20s scheduler firing in between would race them.
        "app.social-publishing.dispatch.cron=-"
})
class PublicationDispatchOutboxIT {

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

    @TestConfiguration
    static class StubDispatcherConfig {
        static final AtomicInteger CALLS = new AtomicInteger();
        static volatile boolean failFirstAsTransient = false;

        @Bean
        @Primary
        PublicationDispatcher stubDispatcher() {
            return (publicationId, idempotencyKey, payload) -> {
                int n = CALLS.incrementAndGet();
                if (failFirstAsTransient && n == 1) {
                    return new PublicationDispatcher.DispatchResult(null, null,
                            PublicationAttemptStatus.FAILED, null, "STUB", "transient", null, true);
                }
                return new PublicationDispatcher.DispatchResult("req", null,
                        PublicationAttemptStatus.SUCCEEDED, "post-" + n, null, null,
                        "https://example.com/p/" + n, true);
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired PublicationJpaRepository publicationRepository;
    @Autowired ProductRepository productRepository;
    @Autowired DispatchDuePublicationsUseCase worker;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String adminToken() {
        return jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "admin@pilarestilo.com", UserRole.ADMIN, List.of(), List.of());
    }

    private UUID publishOneProduct(String label) throws Exception {
        Product product = productRepository.save(Product.create(label, "desc",
                new Money(BigDecimal.valueOf(19990), "CLP"), "https://cdn.example.com/x.jpg",
                ProductCondition.NEW, "Pilar", 2));
        MvcResult res = mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}"))))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(om.readTree(res.getResponse().getContentAsString())
                .get("items").get(0).get("publicationId").asString());
    }

    @Test
    void batch_publish_queues_rows_then_the_worker_publishes_them() throws Exception {
        StubDispatcherConfig.CALLS.set(0);
        StubDispatcherConfig.failFirstAsTransient = false;

        UUID pubId = publishOneProduct("Outbox happy");

        PublicationEntity queued = publicationRepository.findById(pubId).orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(PublicationStatus.APPROVED);
        assertThat(queued.getNextAttemptAt()).isNotNull();

        worker.execute();

        assertThat(publicationRepository.findById(pubId).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.PUBLISHED);
    }

    @Test
    void a_transient_failure_is_retried_and_then_succeeds() throws Exception {
        StubDispatcherConfig.CALLS.set(0);
        StubDispatcherConfig.failFirstAsTransient = true;

        UUID pubId = publishOneProduct("Outbox retry");

        worker.execute();   // attempt 1 -> transient failure -> RETRY_SCHEDULED (backoff 0 min)
        PublicationEntity afterFirst = publicationRepository.findById(pubId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(PublicationStatus.RETRY_SCHEDULED);
        assertThat(afterFirst.getRetryCount()).isEqualTo(1);

        worker.execute();   // attempt 2 -> success
        assertThat(publicationRepository.findById(pubId).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.PUBLISHED);
    }
}
