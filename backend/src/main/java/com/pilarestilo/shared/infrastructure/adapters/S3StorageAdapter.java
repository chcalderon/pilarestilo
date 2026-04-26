package com.pilarestilo.shared.infrastructure.adapters;

import com.pilarestilo.shared.domain.ports.MediaStoragePort;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Component
public class S3StorageAdapter implements MediaStoragePort {

    private final SystemSettingsRepository settingsRepo;
    private final SystemSettingsCryptoService cryptoService;

    public S3StorageAdapter(
            SystemSettingsRepository settingsRepo,
            SystemSettingsCryptoService cryptoService) {
        this.settingsRepo = settingsRepo;
        this.cryptoService = cryptoService;
    }

    @Override
    public String store(InputStream data, String folder, String filename, String contentType) {
        var settings = settingsRepo.get();
        if (!isConfigured(settings)) {
            throw new IllegalStateException(
                "S3 storage is not configured. Set endpoint, bucket, accessKeyId and secretKey in System Settings.");
        }
        String key = folder + "/" + filename;
        try {
            byte[] bytes = data.readAllBytes();
            buildClient(settings).putObject(
                PutObjectRequest.builder()
                    .bucket(settings.getMediaS3Bucket())
                    .key(key)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        } catch (IOException e) {
            throw new RuntimeException("Could not read file for S3 upload", e);
        }
        String base = settings.getMediaS3PublicBaseUrl();
        if (base != null && base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + key;
    }

    @Override
    public void delete(String folder, String filename) {
        var settings = settingsRepo.get();
        if (!isConfigured(settings)) return;
        buildClient(settings).deleteObject(
            DeleteObjectRequest.builder()
                .bucket(settings.getMediaS3Bucket())
                .key(folder + "/" + filename)
                .build()
        );
    }

    private boolean isConfigured(SystemSettings s) {
        return s.getMediaS3Bucket() != null && !s.getMediaS3Bucket().isBlank()
            && s.getMediaS3AccessKeyId() != null && !s.getMediaS3AccessKeyId().isBlank();
    }

    private S3Client buildClient(SystemSettings s) {
        String secretKey = decryptSecret(s.getMediaS3SecretKeyEncrypted());
        var builder = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s.getMediaS3AccessKeyId(), secretKey)))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(s.isMediaS3PathStyleEnabled())
                .build());
        if (s.getMediaS3Endpoint() != null && !s.getMediaS3Endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s.getMediaS3Endpoint()));
        }
        String region = s.getMediaS3Region();
        builder.region(region != null && !region.isBlank() ? Region.of(region) : Region.US_EAST_1);
        return builder.build();
    }

    private String decryptSecret(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return "";
        return cryptoService.decrypt(encrypted);
    }
}
