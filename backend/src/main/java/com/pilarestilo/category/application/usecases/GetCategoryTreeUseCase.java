package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryTreeNode;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetCategoryTreeUseCase {

    private final CategoryRepository categoryRepository;

    public GetCategoryTreeUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeNode> execute() {
        List<Category> all = categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();

        Map<java.util.UUID, List<Category>> byParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(root -> toNode(root, byParent))
                .toList();
    }

    private CategoryTreeNode toNode(Category c, Map<java.util.UUID, List<Category>> byParent) {
        List<CategoryTreeNode> children = byParent.getOrDefault(c.getId(), List.of()).stream()
                .map(child -> toNode(child, byParent))
                .toList();
        return new CategoryTreeNode(
                c.getId(), c.getSlug(), c.getNameEs(), c.getNameEn(),
                c.getSortOrder(), c.isActive(), c.getImageUrl(), children
        );
    }
}
