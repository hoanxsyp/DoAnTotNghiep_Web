package com.webtro.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Cấu hình Redis: dùng cho cache, rate-limit và JWT blacklist (canonical mục 8, §11.11).
 *
 * <p><b>Fail-open cho cache</b> (canonical mục 8): nếu Redis chết, {@link CacheErrorHandler} chỉ
 * log WARN rồi để ứng dụng đọc thẳng DB — cache là tối ưu, không phải phụ thuộc sống còn. (Rate
 * limit và JWT blacklist có chính sách riêng, xử lý trong filter tương ứng, không qua đây.)
 */
@Configuration
public class RedisConfig implements CachingConfigurer {

    /** ObjectMapper riêng cho Redis: giữ kiểu để deserialize object cache đúng lớp. */
    ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.EVERYTHING);
        return mapper;
    }

    GenericJackson2JsonRedisSerializer redisValueSerializer() {
        return new GenericJackson2JsonRedisSerializer(redisObjectMapper());
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer valueSerializer = redisValueSerializer();
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisValueSerializer()));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }

    /**
     * Fail-open: lỗi Redis khi thao tác cache KHÔNG được ném ra làm sập request — chỉ log WARN.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new EvictingCacheErrorHandler();
    }
}
