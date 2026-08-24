package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Characterization tests written before restructuring execute()'s loop to satisfy S135 (at most
 * one break/continue per loop) -- the continue (skip already-migrated) and break (stop on
 * interruption) both live in the same for loop today. A real local HttpServer stands in for the
 * category's image host, since the use case opens its own static HttpClient with no other seam.
 */
@ExtendWith(MockitoExtension.class)
class MigrateCategoryImagesUseCaseTest {

    @Mock CategoryRepository categoryRepository;
    @Mock MediaStorageService mediaStorageService;
    @InjectMocks MigrateCategoryImagesUseCase useCase;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startImageServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/img.jpg", exchange -> {
            byte[] bytes = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/img.jpg";
    }

    @Test
    void migratesAnExternalImageAndUpdatesTheCategory() throws IOException {
        String imageUrl = startImageServer();
        Category cat = Category.create("ropa", "Ropa", "Clothing", null, 1, imageUrl);
        when(categoryRepository.findAll()).thenReturn(List.of(cat));
        when(mediaStorageService.storeOptimizedBytes(any(), any(), eq("categories"), any()))
                .thenReturn("/api/media/categories/ropa.jpg");

        MigrateCategoryImagesUseCase.Result result = useCase.execute();

        assertEquals(1, result.migrated());
        assertEquals(0, result.failed());
        assertEquals("/api/media/categories/ropa.jpg", cat.getImageUrl());
        verify(categoryRepository).save(cat);
    }

    @Test
    void countsAStorageFailureAsFailedWithoutStoppingTheRun() throws IOException {
        String imageUrl = startImageServer();
        Category cat = Category.create("ropa", "Ropa", "Clothing", null, 1, imageUrl);
        when(categoryRepository.findAll()).thenReturn(List.of(cat));
        when(mediaStorageService.storeOptimizedBytes(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("disk full"));

        MigrateCategoryImagesUseCase.Result result = useCase.execute();

        assertEquals(0, result.migrated());
        assertEquals(1, result.failed());
        assertEquals(1, result.errors().size());
        assertEquals("disk full", result.errors().get(0).reason());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void skipsAlreadyMigratedCategories() {
        Category cat = Category.create("ropa", "Ropa", "Clothing", null, 1, "/api/media/categories/ropa.jpg");
        when(categoryRepository.findAll()).thenReturn(List.of(cat));

        MigrateCategoryImagesUseCase.Result result = useCase.execute();

        assertEquals(0, result.migrated());
        assertEquals(0, result.failed());
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void skipsNullImageUrl() {
        Category cat = Category.create("zapatos", "Zapatos", "Shoes", null, 2, null);
        when(categoryRepository.findAll()).thenReturn(List.of(cat));

        MigrateCategoryImagesUseCase.Result result = useCase.execute();

        assertEquals(0, result.migrated());
        assertEquals(0, result.failed());
        verifyNoInteractions(mediaStorageService);
    }
}
