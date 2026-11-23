package com.webtro.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
final class EvictingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        try {
            cache.evict(key);
            log.warn("Redis cache GET lỗi; đã xóa key hỏng cache={} key={}: {}",
                    cache.getName(), key, exception.getMessage());
        } catch (RuntimeException evictException) {
            log.warn("Redis cache GET lỗi và không thể xóa key cache={} key={}: {}; evict={}",
                    cache.getName(), key, exception.getMessage(), evictException.getMessage());
        }
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Redis cache PUT lỗi (bỏ qua) cache={} key={}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Redis cache EVICT lỗi (bỏ qua) cache={} key={}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Redis cache CLEAR lỗi (bỏ qua) cache={}: {}", cache.getName(), exception.getMessage());
    }
}
