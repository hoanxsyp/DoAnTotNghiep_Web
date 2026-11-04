package com.webtro.modules.admin.service.impl;

import com.webtro.common.enums.AiModule;
import com.webtro.modules.admin.mapper.SystemConfigMapper;
import com.webtro.modules.admin.repository.SystemConfigRepository;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.entity.ChatbotMessage;
import com.webtro.modules.ai.entity.PredictionHistory;
import com.webtro.modules.ai.entity.RecommendationLog;
import com.webtro.modules.ai.repository.ChatbotMessageRepository;
import com.webtro.modules.ai.repository.PredictionHistoryRepository;
import com.webtro.modules.ai.repository.RecommendationLogRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAiServiceImplLogsTest {

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private SystemConfigMapper systemConfigMapper;
    @Mock private AuditLogService auditLogService;
    @Mock private RecommendationLogRepository recommendationLogRepository;
    @Mock private PredictionHistoryRepository predictionHistoryRepository;
    @Mock private ChatbotMessageRepository chatbotMessageRepository;
    @Mock private ListingRepository listingRepository;

    @InjectMocks private AdminAiServiceImpl service;

    @Test
    void mergesAllModulesWithGlobalDescendingPagination() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-04T00:00:00Z");
        Pageable requested = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        RecommendationLog recommendation = RecommendationLog.builder()
                .id(1L).createdAt(Instant.parse("2026-11-03T01:00:00Z")).build();
        PredictionHistory price = PredictionHistory.builder()
                .id(2L).createdAt(Instant.parse("2026-11-02T01:00:00Z")).build();
        ChatbotMessage chatbot = ChatbotMessage.builder()
                .id(3L).createdAt(Instant.parse("2026-11-01T01:00:00Z")).build();
        stubPages(from, to, recommendation, price, chatbot);

        var result = service.getLogs(null, from, to, requested);

        assertThat(result.getModule()).isEqualTo("ALL");
        assertThat(result.getResults().getItems()).extracting("module")
                .containsExactly("RECOMMENDATION", "PRICE");
        assertThat(result.getResults().getTotalElements()).isEqualTo(3);
        assertThat(result.getResults().getTotalPages()).isEqualTo(2);
        assertThat(result.getResults().isFirst()).isTrue();
        assertThat(result.getResults().isLast()).isFalse();
    }

    @Test
    void mergesAllModulesAscendingWhenRequested() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-04T00:00:00Z");
        Pageable requested = PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "createdAt"));
        RecommendationLog recommendation = RecommendationLog.builder()
                .id(1L).createdAt(Instant.parse("2026-11-03T01:00:00Z")).build();
        PredictionHistory price = PredictionHistory.builder()
                .id(2L).createdAt(Instant.parse("2026-11-02T01:00:00Z")).build();
        ChatbotMessage chatbot = ChatbotMessage.builder()
                .id(3L).createdAt(Instant.parse("2026-11-01T01:00:00Z")).build();
        stubPages(from, to, recommendation, price, chatbot);

        var result = service.getLogs(null, from, to, requested);

        assertThat(result.getResults().getItems()).extracting("module")
                .containsExactly("CHATBOT", "PRICE", "RECOMMENDATION");
        assertThat(result.getResults().isLast()).isTrue();
    }

    @Test
    void marksFollowingMergedPageAsNotFirst() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-04T00:00:00Z");
        Pageable requested = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        RecommendationLog recommendation = RecommendationLog.builder()
                .id(1L).createdAt(Instant.parse("2026-11-03T01:00:00Z")).build();
        PredictionHistory price = PredictionHistory.builder()
                .id(2L).createdAt(Instant.parse("2026-11-02T01:00:00Z")).build();
        ChatbotMessage chatbot = ChatbotMessage.builder()
                .id(3L).createdAt(Instant.parse("2026-11-01T01:00:00Z")).build();
        stubPages(from, to, recommendation, price, chatbot);

        var result = service.getLogs(null, from, to, requested);

        assertThat(result.getResults().isFirst()).isFalse();
        assertThat(result.getResults().isLast()).isTrue();
        assertThat(result.getResults().getItems()).extracting("module").containsExactly("CHATBOT");
    }

    @Test
    void usesDefaultDescendingOrderWithoutSort() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-04T00:00:00Z");
        RecommendationLog recommendation = RecommendationLog.builder()
                .id(1L).createdAt(Instant.parse("2026-11-03T01:00:00Z")).build();
        stubPages(from, to, recommendation, null, null);

        var result = service.getLogs(null, from, to, PageRequest.of(0, 20));

        assertThat(result.getResults().getItems()).extracting("module").containsExactly("RECOMMENDATION");
    }

    @Test
    void loadsEachSpecificModuleWithinDateRange() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-04T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        RecommendationLog recommendation = RecommendationLog.builder().id(1L).createdAt(from).build();
        PredictionHistory price = PredictionHistory.builder().id(2L).createdAt(from).build();
        ChatbotMessage chatbot = ChatbotMessage.builder().id(3L).createdAt(from).build();
        when(recommendationLogRepository.findByCreatedAtBetween(from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(recommendation), pageable, 1));
        when(predictionHistoryRepository.findByCreatedAtBetween(from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(price), pageable, 1));
        when(chatbotMessageRepository.findByCreatedAtBetween(from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(chatbot), pageable, 1));

        assertThat(service.getLogs(AiModule.RECOMMENDATION, from, to, pageable)
                .getResults().getItems()).extracting("module").containsExactly("RECOMMENDATION");
        assertThat(service.getLogs(AiModule.PRICE, from, to, pageable)
                .getResults().getItems()).extracting("module").containsExactly("PRICE");
        assertThat(service.getLogs(AiModule.CHATBOT, from, to, pageable)
                .getResults().getItems()).extracting("module").containsExactly("CHATBOT");
        verify(recommendationLogRepository).findByCreatedAtBetween(from, to, pageable);
        verify(predictionHistoryRepository).findByCreatedAtBetween(from, to, pageable);
        verify(chatbotMessageRepository).findByCreatedAtBetween(from, to, pageable);
    }

    private void stubPages(Instant from, Instant to, RecommendationLog recommendation,
                           PredictionHistory price, ChatbotMessage chatbot) {
        when(recommendationLogRepository.findByCreatedAtBetween(eq(from), eq(to), any()))
                .thenAnswer(invocation -> page(recommendation, invocation.getArgument(2)));
        when(predictionHistoryRepository.findByCreatedAtBetween(eq(from), eq(to), any()))
                .thenAnswer(invocation -> page(price, invocation.getArgument(2)));
        when(chatbotMessageRepository.findByCreatedAtBetween(eq(from), eq(to), any()))
                .thenAnswer(invocation -> page(chatbot, invocation.getArgument(2)));
    }

    private <T> PageImpl<T> page(T item, Pageable pageable) {
        List<T> items = item == null ? List.of() : List.of(item);
        return new PageImpl<>(items, pageable, items.size());
    }
}
