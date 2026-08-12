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
import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.application.dto.CategoryTreeNode;
import com.pilarestilo.systemsettings.application.dto.PublicStoreSettingsDto;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

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
        // JSON, not JDK serialization: every cached type (CategoryDto, CategoryTreeNode,
        // PublicStoreSettingsDto) is a record and none implements Serializable, so
        // JdkSerializationRedisSerializer threw "Cannot serialize" on every write. The error
        // handler swallowed that as a cache miss, so the cache looked healthy while storing
        // nothing — redis-cli dbsize stayed at 0 and every read hit the database.
        //
        // Each cache is pinned to its concrete type instead of relying on Jackson's default
        // typing. Default typing round-trips a single record fine (@class property) but not a
        // List, where the type id has to move into a wrapper array and the read then fails with
        // "expected VALUE_STRING ... that contains type id". Pinning the type also means nothing
        // in the cache can name a class to instantiate, so there is no deserialization gadget
        // surface to validate. Prefix bumped to v4 so entries in the old format are never read.
        TypeFactory typeFactory = TypeFactory.createDefaultInstance();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(Math.max(30L, ttlSeconds)))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "pe:v5:" + cacheName + "::");

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.PUBLIC_STORE_SETTINGS,
                        typedCache(base, typeFactory.constructType(PublicStoreSettingsDto.class)),
                        CacheNames.CATEGORY_LIST,
                        typedCache(base, typeFactory.constructCollectionType(List.class, CategoryDto.class)),
                        CacheNames.CATEGORY_TREE,
                        typedCache(base, typeFactory.constructCollectionType(List.class, CategoryTreeNode.class))
                ))
                .build();
    }

    private static RedisCacheConfiguration typedCache(RedisCacheConfiguration base, JavaType type) {
        return base.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(type)
                )
        );
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

