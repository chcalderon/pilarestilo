package com.pilarestilo.product.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ProductControllerIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
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
