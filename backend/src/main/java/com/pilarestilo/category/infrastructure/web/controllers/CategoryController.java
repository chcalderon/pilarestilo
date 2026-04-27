package com.pilarestilo.category.infrastructure.web.controllers;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.application.dto.CategoryTreeNode;
import com.pilarestilo.category.application.usecases.*;
import com.pilarestilo.category.infrastructure.web.requests.CreateCategoryRequest;
import com.pilarestilo.category.infrastructure.web.requests.ReorderCategoriesRequest;
import com.pilarestilo.category.infrastructure.web.requests.UpdateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CreateCategoryUseCase createCategory;
    private final UpdateCategoryUseCase updateCategory;
    private final ReorderCategoriesUseCase reorderCategories;
    private final ListCategoriesUseCase listCategories;
    private final GetCategoryTreeUseCase getTree;
    private final GetFeaturedCategoriesUseCase getFeatured;
    private final DeleteCategoryUseCase deleteCategory;

    public CategoryController(CreateCategoryUseCase createCategory,
                              UpdateCategoryUseCase updateCategory,
                              ReorderCategoriesUseCase reorderCategories,
                              ListCategoriesUseCase listCategories,
                              GetCategoryTreeUseCase getTree,
                              GetFeaturedCategoriesUseCase getFeatured,
                              DeleteCategoryUseCase deleteCategory) {
        this.createCategory = createCategory;
        this.updateCategory = updateCategory;
        this.reorderCategories = reorderCategories;
        this.listCategories = listCategories;
        this.getTree = getTree;
        this.getFeatured = getFeatured;
        this.deleteCategory = deleteCategory;
    }

    @GetMapping
    public List<CategoryDto> list() {
        return listCategories.execute();
    }

    @GetMapping("/tree")
    public List<CategoryTreeNode> tree() {
        return getTree.execute();
    }

    @GetMapping("/featured")
    public List<CategoryDto> featured() {
        return getFeatured.execute();
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CreateCategoryRequest req) {
        CategoryDto dto = createCategory.execute(
                req.slug(), req.nameEs(), req.nameEn(),
                req.parentId(), req.sortOrder(), req.imageUrl()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderCategoriesRequest req) {
        reorderCategories.execute(req.items().stream()
                .map(i -> new ReorderCategoriesUseCase.Item(i.id(), i.sortOrder()))
                .toList());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public CategoryDto update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest req) {
        return updateCategory.execute(
                id, req.slug(), req.nameEs(), req.nameEn(),
                req.parentId(), req.sortOrder(), req.active(), req.featured(), req.imageUrl()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteCategory.execute(id);
        return ResponseEntity.noContent().build();
    }
}
