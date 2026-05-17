package com.pilarestilo.navigation.application.usecases;

import com.pilarestilo.navigation.application.dto.NavigationSectionDto;
import com.pilarestilo.navigation.application.mappers.NavigationSectionMapper;
import com.pilarestilo.navigation.domain.ports.NavigationSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListNavigationSectionsUseCase {

    private final NavigationSectionRepository repository;
    private final NavigationSectionMapper mapper;

    public ListNavigationSectionsUseCase(NavigationSectionRepository repository,
                                         NavigationSectionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<NavigationSectionDto> execute() {
        return repository.findAllActiveOrdered()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
