package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMediaBundleEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProductPublicationImageHistoryUseCaseTest {

    @Mock
    PublicationJpaRepository publicationRepository;

    GetProductPublicationImageHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProductPublicationImageHistoryUseCase(publicationRepository);
    }

    @Test
    void returns_distinct_image_urls_most_recent_first() {
        UUID productId = UUID.randomUUID();
        when(publicationRepository.findTop20ByProductIdOrderByCreatedAtDesc(productId)).thenReturn(List.of(
                publicationWithImage("https://cdn.example.com/newest.jpg"),
                publicationWithImage("https://cdn.example.com/newest.jpg"), // reused, should not repeat
                publicationWithImage("https://cdn.example.com/older.jpg")
        ));

        List<String> history = useCase.execute(productId);

        assertEquals(2, history.size());
        assertEquals("https://cdn.example.com/newest.jpg", history.get(0));
        assertEquals("https://cdn.example.com/older.jpg", history.get(1));
    }

    @Test
    void returns_empty_list_when_nothing_was_ever_published() {
        UUID productId = UUID.randomUUID();
        when(publicationRepository.findTop20ByProductIdOrderByCreatedAtDesc(productId)).thenReturn(List.of());

        assertTrue(useCase.execute(productId).isEmpty());
    }

    private PublicationEntity publicationWithImage(String url) {
        PublicationEntity entity = new PublicationEntity();
        PublicationMediaBundleEntity bundle = new PublicationMediaBundleEntity();
        bundle.setPrimaryAssetUrl(url);
        entity.setMediaBundles(List.of(bundle));
        return entity;
    }
}
