package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    public ListCategoriesUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> execute() {
        return categoryRepository.findAll().stream().map(CategoryDto::from).toList();
    }
}
