package com.pilarestilo.navigation.infrastructure.web;

import com.pilarestilo.navigation.application.dto.NavigationSectionDto;
import com.pilarestilo.navigation.application.usecases.DeleteNavigationSectionUseCase;
import com.pilarestilo.navigation.application.usecases.ListNavigationSectionsUseCase;
import com.pilarestilo.navigation.application.usecases.UpsertNavigationSectionUseCase;
import com.pilarestilo.navigation.infrastructure.web.requests.UpsertNavigationSectionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class NavigationSectionController {

    private final ListNavigationSectionsUseCase listSections;
    private final UpsertNavigationSectionUseCase upsertSection;
    private final DeleteNavigationSectionUseCase deleteSection;

    public NavigationSectionController(ListNavigationSectionsUseCase listSections,
                                        UpsertNavigationSectionUseCase upsertSection,
                                        DeleteNavigationSectionUseCase deleteSection) {
        this.listSections = listSections;
        this.upsertSection = upsertSection;
        this.deleteSection = deleteSection;
    }

    @GetMapping("/api/navigation/sections")
    public List<NavigationSectionDto> list() {
        return listSections.execute();
    }

    @PostMapping("/api/admin/navigation/sections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationSectionDto> create(@Valid @RequestBody UpsertNavigationSectionRequest req) {
        NavigationSectionDto dto = upsertSection.execute(null, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/api/admin/navigation/sections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public NavigationSectionDto update(@PathVariable UUID id,
                                        @Valid @RequestBody UpsertNavigationSectionRequest req) {
        return upsertSection.execute(id, req);
    }

    @DeleteMapping("/api/admin/navigation/sections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteSection.execute(id);
        return ResponseEntity.noContent().build();
    }
}
