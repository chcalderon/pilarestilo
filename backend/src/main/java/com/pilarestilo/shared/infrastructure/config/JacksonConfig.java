package com.pilarestilo.shared.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

@Configuration
public class JacksonConfig {

    /**
     * Spring Boot 4 ships Jackson 3, which replaced Jackson2ObjectMapperBuilderCustomizer with
     * JsonMapperBuilderCustomizer and folded java.time support into core — the explicit
     * JavaTimeModule registration Jackson 2 needed is gone. WRITE_DATES_AS_TIMESTAMPS also moved
     * off SerializationFeature onto DateTimeFeature; it is disabled by default in Jackson 3, but
     * stays explicit here so the ISO-8601 wire format does not silently depend on that default.
     */
    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
