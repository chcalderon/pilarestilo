package com.pilarestilo.navigation.application.usecases;

import com.pilarestilo.navigation.application.dto.NavigationSectionDto;
import com.pilarestilo.navigation.application.mappers.NavigationSectionMapper;
import com.pilarestilo.navigation.domain.enums.NavigationLayout;
import com.pilarestilo.navigation.domain.model.NavigationSection;
import com.pilarestilo.navigation.domain.ports.NavigationSectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListNavigationSectionsUseCaseTest {

    @Mock
    private NavigationSectionRepository repository;

    @Spy
    private NavigationSectionMapper mapper;

    @InjectMocks
    private ListNavigationSectionsUseCase useCase;

    @Test
    void execute_returnsOnlyActiveSectionsOrdered() {
        UUID catId = UUID.randomUUID();
        NavigationSection section = NavigationSection.reconstruct(
                UUID.randomUUID(), catId, NavigationLayout.COLUMNS, 4,
                null, "Banner", null, null, true, 0,
                Instant.now(), Instant.now()
        );

        when(repository.findAllActiveOrdered()).thenReturn(List.of(section));

        List<NavigationSectionDto> result = useCase.execute();

        assertEquals(1, result.size());
        NavigationSectionDto dto = result.get(0);
        assertEquals(catId, dto.rootCategoryId());
        assertEquals("COLUMNS", dto.layout());
        assertEquals(4, dto.columnCount());
        assertTrue(dto.active());
        verify(repository).findAllActiveOrdered();
    }

    @Test
    void execute_returnsEmptyListWhenNoSections() {
        when(repository.findAllActiveOrdered()).thenReturn(List.of());

        List<NavigationSectionDto> result = useCase.execute();

        assertTrue(result.isEmpty());
        verify(repository).findAllActiveOrdered();
    }
}
