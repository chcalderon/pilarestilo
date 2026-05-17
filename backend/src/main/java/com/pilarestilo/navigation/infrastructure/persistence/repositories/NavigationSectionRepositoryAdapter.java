package com.pilarestilo.navigation.infrastructure.persistence.repositories;

import com.pilarestilo.navigation.domain.model.NavigationSection;
import com.pilarestilo.navigation.domain.ports.NavigationSectionRepository;
import com.pilarestilo.navigation.infrastructure.persistence.entities.NavigationSectionEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NavigationSectionRepositoryAdapter implements NavigationSectionRepository {

    private final NavigationSectionJpaRepository jpa;

    public NavigationSectionRepositoryAdapter(NavigationSectionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public NavigationSection save(NavigationSection section) {
        return toDomain(jpa.save(toEntity(section)));
    }

    @Override
    public Optional<NavigationSection> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<NavigationSection> findAllActiveOrdered() {
        return jpa.findAllActiveOrdered().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    private NavigationSectionEntity toEntity(NavigationSection s) {
        NavigationSectionEntity e = new NavigationSectionEntity();
        e.setId(s.getId());
        e.setRootCategoryId(s.getRootCategoryId());
        e.setLayout(s.getLayout());
        e.setColumnCount(s.getColumnCount());
        e.setBannerImageUrl(s.getBannerImageUrl());
        e.setBannerTitle(s.getBannerTitle());
        e.setBannerSubtitle(s.getBannerSubtitle());
        e.setBannerLink(s.getBannerLink());
        e.setActive(s.isActive());
        e.setSortOrder(s.getSortOrder());
        e.setCreatedAt(s.getCreatedAt());
        e.setUpdatedAt(s.getUpdatedAt());
        return e;
    }

    private NavigationSection toDomain(NavigationSectionEntity e) {
        return NavigationSection.reconstruct(
                e.getId(),
                e.getRootCategoryId(),
                e.getLayout(),
                e.getColumnCount(),
                e.getBannerImageUrl(),
                e.getBannerTitle(),
                e.getBannerSubtitle(),
                e.getBannerLink(),
                e.isActive(),
                e.getSortOrder(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
