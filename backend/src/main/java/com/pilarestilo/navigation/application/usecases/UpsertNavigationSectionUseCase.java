package com.pilarestilo.navigation.application.usecases;

import com.pilarestilo.navigation.application.dto.NavigationSectionDto;
import com.pilarestilo.navigation.application.mappers.NavigationSectionMapper;
import com.pilarestilo.navigation.domain.enums.NavigationLayout;
import com.pilarestilo.navigation.domain.model.NavigationSection;
import com.pilarestilo.navigation.domain.ports.NavigationSectionRepository;
import com.pilarestilo.navigation.infrastructure.web.requests.UpsertNavigationSectionRequest;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpsertNavigationSectionUseCase {

    private final NavigationSectionRepository repository;
    private final NavigationSectionMapper mapper;

    public UpsertNavigationSectionUseCase(NavigationSectionRepository repository,
                                           NavigationSectionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public NavigationSectionDto execute(UUID id, UpsertNavigationSectionRequest req) {
        NavigationLayout layout;
        try {
            layout = NavigationLayout.valueOf(req.layout());
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid layout: " + req.layout());
        }
        if (id != null) {
            NavigationSection existing = repository.findById(id)
                    .orElseThrow(() -> new DomainException("NavigationSection not found: " + id));
            existing.update(req.rootCategoryId(), layout, req.columnCount(),
                    req.bannerImageUrl(), req.bannerTitle(), req.bannerSubtitle(),
                    req.bannerLink(), req.active(), req.sortOrder());
            return mapper.toDto(repository.save(existing));
        } else {
            NavigationSection section = NavigationSection.create(
                    req.rootCategoryId(), layout, req.columnCount(),
                    req.bannerImageUrl(), req.bannerTitle(), req.bannerSubtitle(),
                    req.bannerLink(), req.active(), req.sortOrder());
            return mapper.toDto(repository.save(section));
        }
    }
}
