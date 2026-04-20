package com.pilarestilo.shared.infrastructure.bootstrap;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

    private final Path mediaRoot;

    public MediaResourceConfig(@Value("${app.media.storage-path:./media}") String mediaStoragePath) {
        this.mediaRoot = Paths.get(mediaStoragePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureMediaDirectory() throws IOException {
        Files.createDirectories(this.mediaRoot);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = this.mediaRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry
                .addResourceHandler("/api/media/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }
}
