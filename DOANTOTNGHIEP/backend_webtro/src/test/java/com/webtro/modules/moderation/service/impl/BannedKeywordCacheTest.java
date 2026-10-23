package com.webtro.modules.moderation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.config.RedisConfig;
import com.webtro.modules.moderation.entity.BannedKeyword;
import com.webtro.modules.moderation.repository.BannedKeywordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUG-07: danh sách từ khóa cấm phải ghi vào Redis rồi đọc lại được bằng đúng serializer của
 * {@link RedisConfig} (trước đây {@code Stream.toList()} làm mọi lần đọc cache lỗi).
 */
class BannedKeywordCacheTest {

    @Test
    @DisplayName("UT-BKW-01 [BUG-07] Cache từ khóa cấm: ghi Redis rồi đọc lại ra đúng List<Snapshot>")
    void snapshotsRoundTripThroughRedisSerializer() throws Exception {
        BannedKeywordRepository repo = mock(BannedKeywordRepository.class);
        when(repo.findByIsActiveTrueAndDeletedAtIsNull()).thenReturn(List.of(
                BannedKeyword.builder().keyword("lừa đảo").normalizedKeyword("lua dao")
                        .severity(BannedKeywordSeverity.values()[0])
                        .appliesTo(BannedKeywordScope.values()[0])
                        .isRegex(false).isActive(true).build()));
        List<BannedKeywordCache.Snapshot> original = new BannedKeywordCache(repo).activeSnapshots();

        Method m = RedisConfig.class.getDeclaredMethod("redisObjectMapper");
        m.setAccessible(true);
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer((ObjectMapper) m.invoke(new RedisConfig()));

        Object back = serializer.deserialize(serializer.serialize(original));

        List<?> list = assertInstanceOf(List.class, back);
        assertEquals(1, list.size());
        BannedKeywordCache.Snapshot s = assertInstanceOf(BannedKeywordCache.Snapshot.class, list.get(0));
        assertEquals(original.get(0), s);
    }
}
