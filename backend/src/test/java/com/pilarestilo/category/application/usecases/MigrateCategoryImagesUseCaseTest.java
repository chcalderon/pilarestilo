package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.infrastructure.services.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrateCategoryImagesUseCaseTest {

    @Mock CategoryRepository categoryRepository;
    @Mock MediaStorageService mediaStorageService;
    @InjectMocks MigrateCategoryImagesUseCase useCase;

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
