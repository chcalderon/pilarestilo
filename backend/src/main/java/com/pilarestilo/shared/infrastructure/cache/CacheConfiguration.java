package com.pilarestilo.shared.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfiguration implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.cache.redis", name = "enabled", havingValue = "true")
    public CacheManager redisCacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${app.cache.redis.ttl-seconds:300}") long ttlSeconds
    ) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(Math.max(30L, ttlSeconds)))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "pe:v3:" + cacheName + "::")
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new JdkSerializationRedisSerializer()
                        )
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.PUBLIC_STORE_SETTINGS, defaultConfig,
                        CacheNames.CATEGORY_LIST, defaultConfig,
                        CacheNames.CATEGORY_TREE, defaultConfig
                ))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager inMemoryCacheManager() {
        return new ConcurrentMapCacheManager(
                CacheNames.PUBLIC_STORE_SETTINGS,
                CacheNames.CATEGORY_LIST,
                CacheNames.CATEGORY_TREE
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.cache.redis", name = "enabled", havingValue = "true")
    public ApplicationRunner clearManagedCachesOnStartup(CacheManager cacheManager) {
        return args -> {
            List<String> managedCaches = List.of(
                    CacheNames.PUBLIC_STORE_SETTINGS,
                    CacheNames.CATEGORY_LIST,
                    CacheNames.CATEGORY_TREE
            );
            for (String cacheName : managedCaches) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache == null) {
                    continue;
                }
                try {
                    cache.clear();
                } catch (RuntimeException e) {
                    log.warn("Cache startup clear failed on '{}': {}", cacheName, e.getMessage());
                }
            }
            log.info("Managed caches cleared on startup: {}", managedCaches);
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET error on '{}' key='{}': {} - treating as cache miss and clearing stale cache",
                        cache.getName(), key, e.getMessage());
                try {
                    cache.clear();
                } catch (RuntimeException clearError) {
                    log.warn("Cache stale entry clear failed on '{}': {}",
                            cache.getName(), clearError.getMessage());
                }
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT error on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT error on '{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR error on '{}': {}", cache.getName(), e.getMessage());
            }
        };
    }
}

