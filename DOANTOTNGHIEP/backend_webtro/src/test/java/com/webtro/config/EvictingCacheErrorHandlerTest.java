package com.webtro.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvictingCacheErrorHandlerTest {

    private EvictingCacheErrorHandler handler;
    private Cache cache;
    private RuntimeException failure;

    @BeforeEach
    void setUp() {
        handler = new EvictingCacheErrorHandler();
        cache = mock(Cache.class);
        failure = new RuntimeException("cache failure");
        when(cache.getName()).thenReturn("catalog");
    }

    @Test
    void evictsCorruptedKeyAfterGetFailure() {
        handler.handleCacheGetError(failure, cache, "key");
        verify(cache).evict("key");
    }

    @Test
    void keepsFailOpenWhenEvictionAlsoFails() {
        doThrow(new RuntimeException("evict failure")).when(cache).evict("key");
        handler.handleCacheGetError(failure, cache, "key");
        verify(cache).evict("key");
    }

    @Test
    void ignoresPutFailure() {
        handler.handleCachePutError(failure, cache, "key", "value");
        verify(cache).getName();
    }

    @Test
    void ignoresEvictFailure() {
        handler.handleCacheEvictError(failure, cache, "key");
        verify(cache).getName();
    }

    @Test
    void ignoresClearFailure() {
        handler.handleCacheClearError(failure, cache);
        verify(cache).getName();
    }
}
