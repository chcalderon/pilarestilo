package com.pilarestilo.review.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration test: review creation → ReviewSummaryListener → product avg_rating updated.
 *
 * Flow:
 *   1. Register a customer user → get JWT
 *   2. Login as admin → get admin JWT
 *   3. Admin creates a product
 *   4. Customer posts a review (rating = 5)
 *   5. Admin approves the review
 *   6. GET /api/products/{id} → avgRating == 5.0, reviewCount == 1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ReviewSummaryIT {

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

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void review_approval_updates_product_avg_rating() throws Exception {

        // 1. Register customer
        String customerBody = om.writeValueAsString(Map.of(
                "email", "reviewer@test.com",
                "password", "password123",
                "fullName", "Test Reviewer"
        ));
        MvcResult regResult = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody))
                .andExpect(status().isCreated())
                .andReturn();
        String customerToken = om.readTree(regResult.getResponse().getContentAsString())
                .get("accessToken").asString();

        // 2. Register + login admin (we register a second user as ADMIN via direct DB is complex;
        //    instead create a customer and promote via register, OR just register with a unique
        //    email and note tests run against Flyway-seeded V6 data which has the admin already.
        //    Use the pre-seeded admin credentials from V2__seed.sql.)
        //    Since Flyway runs V2+V6 in the test container, admin@pilarestilo.com / admin2026 exists.
        String adminLoginBody = om.writeValueAsString(Map.of(
                "email", "admin@pilarestilo.com",
                "password", "admin2026"
        ));
        MvcResult adminLogin = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLoginBody))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = om.readTree(adminLogin.getResponse().getContentAsString())
                .get("accessToken").asString();

        // 3. Admin creates a product
        String productBody = om.writeValueAsString(Map.of(
                "name", "Bolso Review Test",
                "description", "Para test de reseñas",
                "priceAmount", 100000,
                "priceCurrency", "CLP",
                "imageUrl", "https://example.com/img.jpg",
                "condition", "NEW",
                "brand", "TestBrand",
                "stock", 1,
                "active", true
        ));
        MvcResult createProduct = mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(productBody))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = om.readTree(createProduct.getResponse().getContentAsString())
                .get("id").asString();

        // 4. Customer posts a review (rating = 5)
        String reviewBody = om.writeValueAsString(Map.of(
                "rating", 5,
                "title", "Excelente calidad",
                "comment", "El bolso es exactamente como se describe. Muy recomendable."
        ));
        MvcResult createReview = mvc.perform(
                        post("/api/products/{id}/reviews", productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + customerToken)
                                .content(reviewBody))
                .andExpect(status().isCreated())
                .andReturn();
        String reviewId = om.readTree(createReview.getResponse().getContentAsString())
                .get("id").asString();

        // 5. Before approval: avg_rating should still be 0 (only approved reviews count)
        mvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount", is(0)));

        // 6. Admin approves the review
        mvc.perform(patch("/api/reviews/{id}/approve", reviewId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved", is(true)));

        // 7. After approval: ReviewSummaryListener fires → product updated
        mvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount", is(1)))
                .andExpect(jsonPath("$.avgRating", closeTo(5.0, 0.01)));
    }

    @Test
    void review_summary_endpoint_returns_correct_stats() throws Exception {
        // Register customer
        String customerBody = om.writeValueAsString(Map.of(
                "email", "summary_tester@test.com",
                "password", "password123",
                "fullName", "Summary Tester"
        ));
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody))
                .andExpect(status().isCreated())
                .andReturn();
        String customerToken = om.readTree(reg.getResponse().getContentAsString())
                .get("accessToken").asString();

        // Login admin
        String adminLoginBody = om.writeValueAsString(Map.of(
                "email", "admin@pilarestilo.com", "password", "admin2026"
        ));
        MvcResult adminLogin = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLoginBody))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = om.readTree(adminLogin.getResponse().getContentAsString())
                .get("accessToken").asString();

        // Create product
        String productBody = om.writeValueAsString(Map.of(
                "name", "Zapatos Summary Test", "description", "Test",
                "priceAmount", 50000, "priceCurrency", "CLP",
                "imageUrl", "https://example.com/img.jpg",
                "condition", "USED", "brand", "BrandX",
                "stock", 2, "active", true
        ));
        MvcResult created = mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(productBody))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = om.readTree(created.getResponse().getContentAsString()).get("id").asString();

        // Create and approve a 4-star review
        String reviewBody = om.writeValueAsString(Map.of("rating", 4, "title", "Bueno", "comment", "Ok"));
        MvcResult reviewResult = mvc.perform(
                        post("/api/products/{id}/reviews", productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + customerToken)
                                .content(reviewBody))
                .andExpect(status().isCreated())
                .andReturn();
        String reviewId = om.readTree(reviewResult.getResponse().getContentAsString()).get("id").asString();

        mvc.perform(patch("/api/reviews/{id}/approve", reviewId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Check /api/products/{id}/reviews/summary
        mvc.perform(get("/api/products/{id}/reviews/summary", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.avgRating", closeTo(4.0, 0.01)));
    }
}
