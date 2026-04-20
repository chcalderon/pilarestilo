package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public DeleteCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void execute(UUID id) {
        if (categoryRepository.findById(id).isEmpty()) {
            throw new DomainException("Category not found: " + id);
        }
        categoryRepository.deleteById(id);
    }
}
