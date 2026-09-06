package com.pilarestilo.product.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ProductControllerIT {

    private static final UUID SHOE_PRODUCT_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");

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
    ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin@pilarestilo.com", roles = {"ADMIN"})
    void create_product_returns_201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Blazer Gucci",
                "description", "Lana talle M",
                "priceAmount", 150000,
                "imageUrl", "https://example.com/img.jpg",
                "condition", "USED",
                "brand", "Gucci",
                "stock", 1
        ));

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.brand").value("Gucci"));
    }

    @Test
    @WithMockUser(username = "admin@pilarestilo.com", roles = {"ADMIN"})
    void create_product_stores_and_returns_the_image_gallery() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Abrigo con galeria",
                "description", "d",
                "priceAmount", 45000,
                "imageUrl", "https://example.com/cover.jpg",
                "condition", "NEW",
                "brand", "Pilar",
                "stock", 2,
                "galleryImageUrls", List.of("https://example.com/1.jpg", "https://example.com/2.jpg")
        ));

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.galleryImageUrls[0]").value("https://example.com/1.jpg"))
                .andExpect(jsonPath("$.galleryImageUrls[1]").value("https://example.com/2.jpg"));
    }

    @Test
    @WithMockUser(username = "admin@pilarestilo.com", roles = {"ADMIN"})
    void omitting_the_gallery_yields_an_empty_list() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Abrigo sin galeria", "description", "d", "priceAmount", 45000,
                "imageUrl", "https://example.com/cover.jpg", "condition", "NEW", "brand", "Pilar", "stock", 2));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.galleryImageUrls").isArray())
                .andExpect(jsonPath("$.galleryImageUrls").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin@pilarestilo.com", roles = {"ADMIN"})
    void update_shoe_product_accepts_numeric_variant_size() throws Exception {
        String body = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("name", "Pumps Stiletto Nude"),
                Map.entry("description", "Zapatos stiletto en cuero genuino color nude, taco 10cm. Talla 35."),
                Map.entry("priceAmount", 120000),
                Map.entry("priceCurrency", "CLP"),
                Map.entry("listPriceAmount", 144000),
                Map.entry("listPriceCurrency", "CLP"),
                Map.entry("imageUrl", "/api/media/products/product-004.jpg"),
                Map.entry("condition", "USED"),
                Map.entry("brand", "Valentino"),
                Map.entry("stock", 1),
                Map.entry("active", true),
                Map.entry("variants", List.of(
                        Map.of("color", "Nude", "size", "35", "stock", 1)
                ))
        ));

        mvc.perform(put("/api/products/{id}", SHOE_PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].size").value("35"))
                .andExpect(jsonPath("$.sizeStocks[0].size").value("35"));
    }

    @Test
    void list_products_paginated() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void filter_by_condition() throws Exception {
        mvc.perform(get("/api/products").param("condition", "USED"))
                .andExpect(status().isOk());
    }

    @Test
    void search_by_text_returns_ok() throws Exception {
        mvc.perform(get("/api/products/search").param("q", "vestido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    void search_with_active_and_condition_filters_returns_ok() throws Exception {
        mvc.perform(get("/api/products/search")
                        .param("q", "")
                        .param("active", "true")
                        .param("condition", "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    void search_with_category_filter_returns_ok() throws Exception {
        mvc.perform(get("/api/products/search")
                        .param("category", "vestidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    void list_with_created_at_range_returns_ok() throws Exception {
        mvc.perform(get("/api/products")
                        .param("createdFrom", "2026-01-01")
                        .param("createdTo", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    void search_with_created_at_range_and_sort_returns_ok() throws Exception {
        mvc.perform(get("/api/products/search")
                        .param("createdFrom", "2026-01-01")
                        .param("createdTo", "2026-12-31")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }
}
