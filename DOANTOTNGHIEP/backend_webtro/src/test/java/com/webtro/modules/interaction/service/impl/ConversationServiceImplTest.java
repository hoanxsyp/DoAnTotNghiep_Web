package com.webtro.modules.interaction.service.impl;

import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.common.enums.ListingStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.dto.request.CreateConversationRequest;
import com.webtro.modules.interaction.dto.request.SendMessageRequest;
import com.webtro.modules.interaction.entity.Conversation;
import com.webtro.modules.interaction.entity.Message;
import com.webtro.modules.interaction.mapper.ConversationMapper;
import com.webtro.modules.interaction.repository.ConversationRepository;
import com.webtro.modules.interaction.repository.MessageRepository;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.interaction.websocket.ChatRealtimePublisher;
import com.webtro.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT nhắn tin giữa người thuê và chủ trọ (UT-CHAT-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceImplTest {

    private static final long TENANT_ID = 30L;
    private static final long STRANGER_ID = 31L;
    private static final long OWNER_ID = 40L;
    private static final long LISTING_ID = 20L;
    private static final long CONV_ID = 500L;

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ListingGateway listingGateway;
    @Mock
    private UserGateway userGateway;
    @Mock
    private BannedKeywordGateway bannedKeywordGateway;
    @Mock
    private ChatRealtimePublisher realtimePublisher;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private SystemConfigService systemConfig;

    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConversationServiceImpl(conversationRepository, messageRepository, listingGateway, userGateway,
                bannedKeywordGateway, new ConversationMapper(), realtimePublisher, rateLimitService, systemConfig);
        when(systemConfig.getInt(ConfigKey.CHAT_MESSAGE_MAX_LENGTH)).thenReturn(1000);
        when(systemConfig.getInt(ConfigKey.SPAM_MESSAGE_PER_MINUTE)).thenReturn(20);
        when(bannedKeywordGateway.scan(anyString(), any()))
                .thenReturn(new BannedKeywordGateway.ScanResult(Optional.empty(), List.of()));
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(listing(true));
        when(listingGateway.findBrief(LISTING_ID)).thenReturn(Optional.of(listing(true)));
        when(userGateway.isLandlordChatEnabled(OWNER_ID)).thenReturn(true);
        when(userGateway.getBriefs(anyCollection())).thenReturn(Map.of());
        when(userGateway.findBrief(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(CONV_ID);
            }
            return c;
        });
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(900L);
            m.setCreatedAt(Instant.now());
            return m;
        });
    }

    @Test
    @DisplayName("UT-CHAT-01 Tenant mở hội thoại mới → tạo conversation + tin nhắn đầu, chủ trọ +1 chưa đọc")
    void createNewConversation() {
        when(conversationRepository.findByListingIdAndTenantIdAndDeletedAtIsNull(LISTING_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        var response = service.createConversation(create("Chào anh, phòng còn trống không ạ?"), TENANT_ID);

        assertFalse(response.getAlreadyExisted());
        assertEquals("TENANT", response.getMyRole());
        assertEquals(OWNER_ID, response.getLandlordId());
        ArgumentCaptor<Conversation> conv = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, org.mockito.Mockito.atLeastOnce()).save(conv.capture());
        Conversation saved = conv.getValue();
        assertEquals(1, saved.getLandlordUnreadCount());
        assertEquals(0, saved.getTenantUnreadCount());
        assertEquals(1, saved.getMessageCount());
        assertNull(saved.getFirstResponseAt());
    }

    @Test
    @DisplayName("UT-CHAT-02 Mở lại hội thoại cùng tin → dùng lại conversation cũ (alreadyExisted = true)")
    void reuseExistingConversation() {
        Conversation existing = conversation(0, 2, 3);
        when(conversationRepository.findByListingIdAndTenantIdAndDeletedAtIsNull(LISTING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        var response = service.createConversation(create("Em hỏi thêm chút ạ, phòng có gác không?"), TENANT_ID);

        assertTrue(response.getAlreadyExisted());
        assertEquals(CONV_ID, response.getId());
        assertEquals(4, existing.getMessageCount());
        assertEquals(3, existing.getLandlordUnreadCount());
    }

    @Test
    @DisplayName("UT-CHAT-03 Nhắn vào tin không công khai → LISTING_NOT_ACTIVE")
    void createOnNonPublicListing() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(listing(false));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createConversation(create("Chào anh, phòng còn không ạ?"), TENANT_ID));

        assertEquals(ErrorCode.LISTING_NOT_ACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CHAT-04 Nhắn vào tin của chính mình → CONTACT_FORBIDDEN_SELF")
    void createOnOwnListing() {
        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createConversation(create("Tự nhắn cho mình được không?"), OWNER_ID));

        assertEquals(ErrorCode.CONTACT_FORBIDDEN_SELF, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CHAT-05 Tenant đang bị hạn chế liên hệ (spam) → CONTACT_RESTRICTED_SPAM")
    void createWhileContactRestricted() {
        when(userGateway.isContactRestricted(TENANT_ID)).thenReturn(true);

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.createConversation(create("Chào anh, phòng còn không ạ?"), TENANT_ID));

        assertEquals(ErrorCode.CONTACT_RESTRICTED_SPAM, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CHAT-06 Chủ trọ tắt chat → CHAT_DISABLED_BY_LANDLORD (tenant gọi điện thay thế)")
    void createWhenLandlordDisabledChat() {
        when(userGateway.isLandlordChatEnabled(OWNER_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createConversation(create("Chào anh, phòng còn không ạ?"), TENANT_ID));

        assertEquals(ErrorCode.CHAT_DISABLED_BY_LANDLORD, ex.getErrorCode());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CHAT-07 Tin nhắn chứa từ cấm (kể cả MILD) → BANNED_KEYWORD_DETECTED")
    void createWithBannedKeyword() {
        when(bannedKeywordGateway.scan(anyString(), any())).thenReturn(
                new BannedKeywordGateway.ScanResult(Optional.of(BannedKeywordSeverity.MILD), List.of("xxx")));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createConversation(create("nội dung có xxx trong này"), TENANT_ID));

        assertEquals(ErrorCode.BANNED_KEYWORD_DETECTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CHAT-08 Người ngoài xem hội thoại của người khác → CONVERSATION_FORBIDDEN")
    void strangerCannotReadConversation() {
        when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conversation(0, 0, 1)));

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.listMessages(CONV_ID, STRANGER_ID, Pageable.unpaged()));

        assertEquals(ErrorCode.CONVERSATION_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CHAT-09 Hội thoại không tồn tại → CONVERSATION_NOT_FOUND")
    void missingConversation() {
        when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getConversation(CONV_ID, TENANT_ID));
    }

    @Test
    @DisplayName("UT-CHAT-10 Tenant gửi tin trong hội thoại → chủ trọ +1 chưa đọc")
    void tenantSendsMessage() {
        Conversation c = conversation(0, 0, 1);
        when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(c));

        var response = service.sendMessage(CONV_ID, send("Tối nay em qua xem được không ạ?"), TENANT_ID);

        assertTrue(response.getSentByMe());
        assertEquals(1, c.getLandlordUnreadCount());
        assertEquals(2, c.getMessageCount());
    }

    @Test
    @DisplayName("UT-CHAT-11 Chủ trọ trả lời → tenant +1 chưa đọc, ghi first_response_at")
    void landlordReplyUpdatesTenantUnread() {
        Conversation c = conversation(0, 1, 1);
        when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(c));

        service.sendMessage(CONV_ID, send("Được em nhé"), OWNER_ID);

        assertEquals(1, c.getTenantUnreadCount());
        assertNotNull(c.getFirstResponseAt());
    }

    @Test
    @DisplayName("UT-CHAT-12 Tin nhắn dài hơn giới hạn cấu hình → VALIDATION_FAILED")
    void messageTooLong() {
        when(systemConfig.getInt(ConfigKey.CHAT_MESSAGE_MAX_LENGTH)).thenReturn(10);
        when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conversation(0, 0, 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendMessage(CONV_ID, send("Tin nhắn này dài hơn mười ký tự"), TENANT_ID));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CHAT-13 Tenant đánh dấu đã đọc → chỉ tin của chủ trọ được đánh dấu, tenantUnread = 0")
    void markReadOnlyMarksPartnerMessages() {
        Conversation c = conversation(2, 1, 3);
        when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(c));
        Message fromOwner = Message.builder().conversation(c).senderId(OWNER_ID).content("a").isRead(false).build();
        Message fromMe = Message.builder().conversation(c).senderId(TENANT_ID).content("b").isRead(false).build();
        when(messageRepository.findByConversation_IdAndDeletedAtIsNullOrderByCreatedAtAsc(eq(CONV_ID), any()))
                .thenReturn(new PageImpl<>(List.of(fromOwner, fromMe)));

        service.markRead(CONV_ID, TENANT_ID);

        assertTrue(fromOwner.getIsRead());
        assertNotNull(fromOwner.getReadAt());
        assertFalse(fromMe.getIsRead());
        assertEquals(0, c.getTenantUnreadCount());
        assertEquals(1, c.getLandlordUnreadCount());
    }

    private Conversation conversation(int tenantUnread, int landlordUnread, int messageCount) {
        Conversation c = Conversation.builder()
                .listingId(LISTING_ID)
                .tenantId(TENANT_ID)
                .tenantUnreadCount(tenantUnread)
                .landlordUnreadCount(landlordUnread)
                .messageCount(messageCount)
                .build();
        c.setId(CONV_ID);
        return c;
    }

    private ListingGateway.ListingBrief listing(boolean publiclyVisible) {
        return new ListingGateway.ListingBrief(LISTING_ID, OWNER_ID, "Phòng test", "phong-test", null,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25), "Hà Nội",
                publiclyVisible ? ListingStatus.ACTIVE : ListingStatus.HIDDEN, publiclyVisible,
                BigDecimal.ZERO, 0, 900L, null);
    }

    private CreateConversationRequest create(String message) {
        CreateConversationRequest r = new CreateConversationRequest();
        r.setListingId(LISTING_ID);
        r.setInitialMessage(message);
        return r;
    }

    private SendMessageRequest send(String content) {
        SendMessageRequest r = new SendMessageRequest();
        r.setContent(content);
        return r;
    }
}
