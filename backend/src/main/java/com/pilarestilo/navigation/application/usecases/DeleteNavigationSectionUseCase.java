package com.pilarestilo.navigation.application.usecases;

import com.pilarestilo.navigation.domain.ports.NavigationSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteNavigationSectionUseCase {

    private final NavigationSectionRepository repository;

    public DeleteNavigationSectionUseCase(NavigationSectionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(UUID id) {
        repository.deleteById(id);
    }
}
