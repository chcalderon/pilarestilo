package com.pilarestilo.navigation.domain.ports;

import com.pilarestilo.navigation.domain.model.NavigationSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NavigationSectionRepository {
    NavigationSection save(NavigationSection section);
    Optional<NavigationSection> findById(UUID id);
    List<NavigationSection> findAllActiveOrdered();
    void deleteById(UUID id);
}
