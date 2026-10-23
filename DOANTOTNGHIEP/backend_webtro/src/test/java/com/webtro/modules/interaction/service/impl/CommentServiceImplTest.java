package com.webtro.modules.interaction.service.impl;

import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.common.enums.CommentStatus;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.UserStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.RateLimitException;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.dto.request.CreateCommentRequest;
import com.webtro.modules.interaction.dto.request.UpdateCommentRequest;
import com.webtro.modules.interaction.entity.Comment;
import com.webtro.modules.interaction.mapper.CommentMapper;
import com.webtro.modules.interaction.repository.CommentRepository;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.CustomUserDetails;
import com.webtro.security.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT bình luận của người thuê: tạo, trả lời, sửa, xóa trong phạm vi cho phép (UT-CMT-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceImplTest {

    private static final long TENANT_ID = 30L;
    private static final long OTHER_TENANT_ID = 31L;
    private static final long OWNER_ID = 40L;
    private static final long LISTING_ID = 20L;
    private static final long COMMENT_ID = 100L;

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ListingGateway listingGateway;
    @Mock
    private UserGateway userGateway;
    @Mock
    private BannedKeywordGateway bannedKeywordGateway;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private SystemConfigService systemConfig;
    @Mock
    private AuditLogService auditLogService;

    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(commentRepository, listingGateway, userGateway, bannedKeywordGateway,
                new CommentMapper(), notificationService, rateLimitService, systemConfig, auditLogService);
        when(systemConfig.getInt(ConfigKey.SPAM_COMMENT_PER_MINUTE)).thenReturn(5);
        when(systemConfig.getInt(ConfigKey.COMMENT_EDIT_WINDOW_MINUTES)).thenReturn(30);
        when(bannedKeywordGateway.scan(anyString(), any())).thenReturn(clean());
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(listing(OWNER_ID, true));
        when(listingGateway.findBrief(LISTING_ID)).thenReturn(Optional.of(listing(OWNER_ID, true)));
        when(userGateway.findBrief(any())).thenReturn(Optional.empty());
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(COMMENT_ID + 1);
            }
            if (c.getCreatedAt() == null) {
                c.setCreatedAt(Instant.now());
            }
            return c;
        });
        loginAs(TENANT_ID, RoleCode.TENANT);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ================================================================== tạo

    @Test
    @DisplayName("UT-CMT-01 Bình luận gốc trên tin công khai → VISIBLE, lọc HTML, báo chủ tin, đồng bộ comment_count")
    void createRootComment() {
        when(commentRepository.countByListingIdAndStatusAndDeletedAtIsNull(LISTING_ID, CommentStatus.VISIBLE))
                .thenReturn(4L);

        var response = service.createComment(LISTING_ID, create("<script>x</script>Phòng còn không ạ?", null), TENANT_ID);

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertEquals(CommentStatus.VISIBLE, saved.getValue().getStatus());
        assertEquals(false, saved.getValue().getContent().contains("<script>"));
        assertEquals(false, saved.getValue().getIsOwnerReply());
        assertEquals(true, response.getEditable());
        verify(listingGateway).setCommentCount(LISTING_ID, 4);
        verify(notificationService).notifyUser(eq(OWNER_ID), eq(NotificationType.NEW_COMMENT), anyString(), anyString());
    }

    @Test
    @DisplayName("UT-CMT-02 Vượt rate limit bình luận/phút → 429, không lưu")
    void createRateLimited() {
        doThrow(new RateLimitException(ErrorCode.RATE_LIMIT_EXCEEDED, 60L))
                .when(rateLimitService).checkLimit(eq("comment"), eq(String.valueOf(TENANT_ID)), anyInt(), any(Duration.class));

        assertThrows(RateLimitException.class,
                () -> service.createComment(LISTING_ID, create("Phòng còn không ạ?", null), TENANT_ID));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CMT-03 Tài khoản bị tạm khóa bình luận → COMMENT_SUSPENDED")
    void createWhileSuspended() {
        when(userGateway.isCommentSuspended(TENANT_ID)).thenReturn(true);

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.createComment(LISTING_ID, create("Phòng còn không ạ?", null), TENANT_ID));

        assertEquals(ErrorCode.COMMENT_SUSPENDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CMT-04 Bình luận vào tin không công khai → LISTING_NOT_ACTIVE")
    void createOnNonPublicListing() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(listing(OWNER_ID, false));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createComment(LISTING_ID, create("Phòng còn không ạ?", null), TENANT_ID));

        assertEquals(ErrorCode.LISTING_NOT_ACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CMT-05 Chứa từ cấm mức SEVERE → BANNED_KEYWORD_DETECTED")
    void createWithSevereKeyword() {
        when(bannedKeywordGateway.scan(anyString(), any())).thenReturn(
                new BannedKeywordGateway.ScanResult(Optional.of(BannedKeywordSeverity.SEVERE), List.of("lừa đảo")));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createComment(LISTING_ID, create("đồ lừa đảo", null), TENANT_ID));

        assertEquals(ErrorCode.BANNED_KEYWORD_DETECTED, ex.getErrorCode());
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CMT-06 Từ cấm mức MILD → lưu PENDING (chờ duyệt), KHÔNG báo chủ tin")
    void createWithMildKeyword() {
        when(bannedKeywordGateway.scan(anyString(), any())).thenReturn(
                new BannedKeywordGateway.ScanResult(Optional.of(BannedKeywordSeverity.MILD), List.of("xxx")));

        var response = service.createComment(LISTING_ID, create("hơi xxx đấy", null), TENANT_ID);

        assertEquals(CommentStatus.PENDING.name(), response.getStatus());
        verify(notificationService, never()).notifyUser(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("UT-CMT-07 Bình luận vào tin ở ghép của CHÍNH MÌNH → không tự báo cho mình")
    void createOnOwnListingDoesNotNotifySelf() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(listing(TENANT_ID, true));

        service.createComment(LISTING_ID, create("Mình bổ sung thông tin", null), TENANT_ID);

        verify(notificationService, never()).notifyUser(any(), any(), anyString(), anyString());
    }

    // ================================================================== trả lời

    @Test
    @DisplayName("UT-CMT-08 Trả lời bình luận gốc → reply_count của gốc +1")
    void replyIncrementsParentCount() {
        Comment parent = comment(COMMENT_ID, OTHER_TENANT_ID, null, CommentStatus.VISIBLE, Instant.now());
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(parent));

        service.replyComment(COMMENT_ID, update("Mình cũng quan tâm"), TENANT_ID);

        assertEquals(1, parent.getReplyCount());
    }

    @Test
    @DisplayName("UT-CMT-09 Trả lời một reply (lồng 2 cấp) → COMMENT_NESTING_TOO_DEEP")
    void replyToReplyIsRejected() {
        Comment root = comment(COMMENT_ID, OTHER_TENANT_ID, null, CommentStatus.VISIBLE, Instant.now());
        Comment reply = comment(COMMENT_ID + 5, OTHER_TENANT_ID, root, CommentStatus.VISIBLE, Instant.now());
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID + 5)).thenReturn(Optional.of(reply));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.replyComment(COMMENT_ID + 5, update("trả lời sâu"), TENANT_ID));

        assertEquals(ErrorCode.COMMENT_NESTING_TOO_DEEP, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CMT-10 parentCommentId thuộc tin khác → COMMENT_PARENT_MISMATCH")
    void parentFromAnotherListing() {
        Comment parent = comment(COMMENT_ID, OTHER_TENANT_ID, null, CommentStatus.VISIBLE, Instant.now());
        parent.setListingId(999L);
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(parent));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createComment(LISTING_ID, create("trả lời", COMMENT_ID), TENANT_ID));

        assertEquals(ErrorCode.COMMENT_PARENT_MISMATCH, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CMT-11 Trả lời bình luận đang PENDING/HIDDEN → COMMENT_NOT_VISIBLE")
    void replyToHiddenParent() {
        Comment parent = comment(COMMENT_ID, OTHER_TENANT_ID, null, CommentStatus.HIDDEN, Instant.now());
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(parent));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.replyComment(COMMENT_ID, update("trả lời"), TENANT_ID));

        assertEquals(ErrorCode.COMMENT_NOT_VISIBLE, ex.getErrorCode());
    }

    // ================================================================== sửa

    @Test
    @DisplayName("UT-CMT-12 Sửa bình luận của mình trong 30 phút → cập nhật nội dung + editedAt")
    void updateOwnWithinWindow() {
        Comment c = comment(COMMENT_ID, TENANT_ID, null, CommentStatus.VISIBLE, Instant.now().minus(Duration.ofMinutes(10)));
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(c));

        var response = service.updateComment(COMMENT_ID, update("Nội dung mới"), TENANT_ID);

        assertEquals("Nội dung mới", c.getContent());
        assertNotNull(c.getEditedAt());
        assertEquals("Nội dung mới", response.getContent());
    }

    @Test
    @DisplayName("UT-CMT-13 Sửa bình luận của người khác → COMMENT_FORBIDDEN")
    void updateOthersComment() {
        Comment c = comment(COMMENT_ID, OTHER_TENANT_ID, null, CommentStatus.VISIBLE, Instant.now());
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(c));

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.updateComment(COMMENT_ID, update("sửa trộm"), TENANT_ID));

        assertEquals(ErrorCode.COMMENT_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CMT-14 Sửa sau 30 phút → COMMENT_EDIT_WINDOW_EXPIRED")
    void updateAfterWindow() {
        Comment c = comment(COMMENT_ID, TENANT_ID, null, CommentStatus.VISIBLE, Instant.now().minus(Duration.ofMinutes(31)));
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(c));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateComment(COMMENT_ID, update("muộn rồi"), TENANT_ID));

        assertEquals(ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED, ex.getErrorCode());
    }

    // ================================================================== xóa

    @Test
    @DisplayName("UT-CMT-15 Xóa reply của mình trong 30 phút → DELETED mềm, reply_count gốc -1")
    void deleteOwnReply() {
        Comment root = comment(COMMENT_ID, OTHER_TENANT_ID, null, CommentStatus.VISIBLE, Instant.now());
        root.setReplyCount(1);
        Comment reply = comment(COMMENT_ID + 1, TENANT_ID, root, CommentStatus.VISIBLE, Instant.now());
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID + 1)).thenReturn(Optional.of(reply));

        service.deleteComment(COMMENT_ID + 1, TENANT_ID);

        assertEquals(CommentStatus.DELETED, reply.getStatus());
        assertNotNull(reply.getDeletedAt());
        assertEquals(0, root.getReplyCount());
    }

    @Test
    @DisplayName("UT-CMT-16 Tenant xóa bình luận của người khác → COMMENT_FORBIDDEN")
    void deleteOthersComment() {
        Comment c = comment(COMMENT_ID, OTHER_TENANT_ID, null, CommentStatus.VISIBLE, Instant.now());
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(c));

        assertThrows(ForbiddenException.class, () -> service.deleteComment(COMMENT_ID, TENANT_ID));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CMT-17 Tenant xóa bình luận của mình sau 30 phút → COMMENT_EDIT_WINDOW_EXPIRED")
    void deleteOwnAfterWindow() {
        Comment c = comment(COMMENT_ID, TENANT_ID, null, CommentStatus.VISIBLE, Instant.now().minus(Duration.ofHours(2)));
        when(commentRepository.findByIdAndDeletedAtIsNull(COMMENT_ID)).thenReturn(Optional.of(c));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.deleteComment(COMMENT_ID, TENANT_ID));

        assertEquals(ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED, ex.getErrorCode());
    }

    // ================================================================== helper

    private void loginAs(long id, String role) {
        CustomUserDetails principal = new CustomUserDetails(id, id + "@test.local", "x", UserStatus.ACTIVE, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static BannedKeywordGateway.ScanResult clean() {
        return new BannedKeywordGateway.ScanResult(Optional.empty(), List.of());
    }

    private ListingGateway.ListingBrief listing(long ownerId, boolean publiclyVisible) {
        return new ListingGateway.ListingBrief(LISTING_ID, ownerId, "Phòng test", "phong-test", null,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25), "Hà Nội",
                publiclyVisible ? ListingStatus.ACTIVE : ListingStatus.HIDDEN, publiclyVisible,
                BigDecimal.ZERO, 0, 900L, null);
    }

    private Comment comment(long id, long userId, Comment parent, CommentStatus status, Instant createdAt) {
        Comment c = Comment.builder()
                .listingId(LISTING_ID)
                .userId(userId)
                .parent(parent)
                .content("Nội dung cũ")
                .status(status)
                .build();
        c.setId(id);
        c.setCreatedAt(createdAt);
        return c;
    }

    private CreateCommentRequest create(String content, Long parentId) {
        return CreateCommentRequest.builder().content(content).parentCommentId(parentId).build();
    }

    private UpdateCommentRequest update(String content) {
        return UpdateCommentRequest.builder().content(content).build();
    }
}
