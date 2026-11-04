package com.webtro.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void serializerRoundTripsFinalImmutableCollection() {
        GenericJackson2JsonRedisSerializer serializer =
                new RedisConfig().redisValueSerializer();
        List<String> source = List.of("one", "two");

        Object restored = serializer.deserialize(serializer.serialize(source));

        assertEquals(source, restored);
    }

    @Test
    void providesEvictingCacheErrorHandler() {
        assertInstanceOf(EvictingCacheErrorHandler.class, new RedisConfig().errorHandler());
    }

    @Test
    void createsRedisTemplateAndCacheManagerWithSharedSerializerFactory() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        assertNotNull(config.redisTemplate(factory));
        assertNotNull(config.cacheManager(factory));
    }
}
