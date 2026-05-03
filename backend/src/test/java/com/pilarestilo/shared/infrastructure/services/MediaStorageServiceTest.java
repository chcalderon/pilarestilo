package com.pilarestilo.shared.infrastructure.services;

import com.pilarestilo.shared.infrastructure.adapters.LocalFileStorageAdapter;
import com.pilarestilo.shared.infrastructure.adapters.S3StorageAdapter;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @Mock LocalFileStorageAdapter localAdapter;
    @Mock S3StorageAdapter s3Adapter;
    @Mock SystemSettingsRepository settingsRepo;
    @Mock ImageOptimizerService imageOptimizer;
    @InjectMocks MediaStorageService service;

    private static final byte[] FAKE_BYTES = "data".getBytes();
    private static final ImageOptimizerService.OptimizedImage OPTIMIZED =
            new ImageOptimizerService.OptimizedImage(FAKE_BYTES, "jpg");

    @BeforeEach
    void stubOptimizer() throws Exception {
        when(imageOptimizer.optimize(any(), anyString())).thenReturn(OPTIMIZED);
    }

    @Test
    void usesLocalAdapterWhenProviderIsLocal() throws Exception {
        var settings = mock(SystemSettings.class);
        when(settings.getMediaStorageProvider())
            .thenReturn(com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider.LOCAL);
        when(settingsRepo.get()).thenReturn(settings);
        when(localAdapter.store(any(), anyString(), anyString(), anyString()))
            .thenReturn("/api/media/products/test.jpg");

        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", FAKE_BYTES);
        service.store(file, "products");

        verify(localAdapter).store(any(), eq("products"), anyString(), eq("image/jpg"));
        verifyNoInteractions(s3Adapter);
    }

    @Test
    void usesS3AdapterWhenProviderIsS3Compatible() throws Exception {
        var settings = mock(SystemSettings.class);
        when(settings.getMediaStorageProvider())
            .thenReturn(com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider.S3_COMPATIBLE);
        when(settingsRepo.get()).thenReturn(settings);
        when(s3Adapter.store(any(), anyString(), anyString(), anyString()))
            .thenReturn("https://bucket.example.com/products/test.jpg");

        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", FAKE_BYTES);
        service.store(file, "products");

        verify(s3Adapter).store(any(), eq("products"), anyString(), eq("image/jpg"));
        verifyNoInteractions(localAdapter);
    }
}
