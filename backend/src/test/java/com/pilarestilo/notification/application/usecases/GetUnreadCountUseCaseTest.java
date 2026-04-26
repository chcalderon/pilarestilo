package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUnreadCountUseCaseTest {

    @Mock InAppNotificationRepository repository;
    @InjectMocks GetUnreadCountUseCase useCase;

    @Test
    void returnsCountFromRepository() {
        UUID userId = UUID.randomUUID();
        when(repository.countUnreadByUserId(userId)).thenReturn(3L);
        assertEquals(3L, useCase.execute(userId));
    }
}
