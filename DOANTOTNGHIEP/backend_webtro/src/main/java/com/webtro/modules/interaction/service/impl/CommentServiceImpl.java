package com.webtro.modules.interaction.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.AuditActorType;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.CommentStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.dto.request.CreateCommentRequest;
import com.webtro.modules.interaction.dto.request.UpdateCommentRequest;
import com.webtro.modules.interaction.dto.response.AdminCommentResponse;
import com.webtro.modules.interaction.dto.response.AuthorResponse;
import com.webtro.modules.interaction.dto.response.CommentPageResponse;
import com.webtro.modules.interaction.dto.response.CommentResponse;
import com.webtro.modules.interaction.entity.Comment;
import com.webtro.modules.interaction.mapper.CommentMapper;
import com.webtro.modules.interaction.repository.CommentRepository;
import com.webtro.modules.interaction.service.CommentService;
import com.webtro.modules.interaction.specification.CommentSpecifications;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.RateLimitService;
import com.webtro.security.SecurityUtils;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hiện thực nghiệp vụ bình luận (CMT) — canonical 4.7.1–4.7.5, {@code [§3.11]}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ListingGateway listingGateway;
    private final UserGateway userGateway;
    private final BannedKeywordGateway bannedKeywordGateway;
    private final CommentMapper commentMapper;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final SystemConfigService systemConfig;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public CommentPageResponse listComments(Long listingId, boolean includeReplies, Long viewerId,
                                            Pageable pageable) {
        ListingGateway.ListingBrief listing = listingGateway.getBrief(listingId);
        Long ownerId = listing.ownerId();
        boolean viewerModerates = SecurityUtils.hasAnyRole(RoleCode.MODERATOR, RoleCode.ADMIN);

        Page<Comment> page = commentRepository.findByListingIdAndParentIsNullAndStatusAndDeletedAtIsNull(
                listingId, CommentStatus.VISIBLE, pageable);

        // Gom tất cả tác giả (gốc + reply) để nạp một lần.
        List<Comment> roots = page.getContent();
        Map<Long, List<Comment>> repliesByRoot = includeReplies ? roots.stream()
                .collect(Collectors.toMap(Comment::getId,
                        c -> commentRepository.findByParent_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                                c.getId(), CommentStatus.VISIBLE)))
                : Map.of();

        List<Long> authorIds = roots.stream().flatMap(r -> {
            List<Comment> rep = repliesByRoot.getOrDefault(r.getId(), List.of());
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(r.getUserId()),
                    rep.stream().map(Comment::getUserId));
        }).distinct().toList();
        Map<Long, UserGateway.UserBrief> authors = userGateway.getBriefs(authorIds);

        PageResponse<CommentResponse> mapped = PageResponse.from(page, root -> {
            List<CommentResponse> replies = repliesByRoot.getOrDefault(root.getId(), List.of()).stream()
                    .map(rep -> toResponse(rep, authors, ownerId, viewerId, viewerModerates, List.of()))
                    .toList();
            return toResponse(root, authors, ownerId, viewerId, viewerModerates, replies);
        });

        return CommentPageResponse.builder()
                .page(mapped)
                .build();
    }

    @Override
    @Transactional
    public CommentResponse createComment(Long listingId, CreateCommentRequest request, Long userId) {
        int perMinute = systemConfig.getInt(ConfigKey.SPAM_COMMENT_PER_MINUTE);
        rateLimitService.checkLimit("comment", String.valueOf(userId), perMinute, Duration.ofMinutes(1));

        if (userGateway.isCommentSuspended(userId)) {
            throw new ForbiddenException(ErrorCode.COMMENT_SUSPENDED);
        }

        ListingGateway.ListingBrief listing = listingGateway.getBrief(listingId);
        if (!listing.publiclyVisible()) {
            throw new BusinessRuleException(ErrorCode.LISTING_NOT_ACTIVE);
        }

        Comment parent = resolveParent(request.getParentCommentId(), listingId);

        BannedKeywordGateway.ScanResult scan = bannedKeywordGateway.scan(request.getContent(), BannedKeywordScope.COMMENT);
        if (scan.isSevere()) {
            throw new BusinessRuleException(ErrorCode.BANNED_KEYWORD_DETECTED,
                    "Nội dung chứa từ ngữ không được phép: " + String.join(", ", scan.matched()));
        }
        boolean mild = scan.isMild();
        CommentStatus status = mild ? CommentStatus.PENDING : CommentStatus.VISIBLE;

        Comment comment = Comment.builder()
                .listingId(listingId)
                .userId(userId)
                .parent(parent)
                .content(HtmlSanitizer.stripAllHtml(request.getContent()))
                .status(status)
                .isOwnerReply(parent != null && userId.equals(listing.ownerId()))
                .containsBannedKeyword(mild)
                .build();
        comment = commentRepository.save(comment);

        if (parent != null) {
            parent.setReplyCount(parent.getReplyCount() + 1);
            commentRepository.save(parent);
        }
        syncCommentCount(listingId);

        // Thông báo chủ trọ (IN_APP) khi bình luận hiển thị và không phải tự bình luận.
        if (status == CommentStatus.VISIBLE && !userId.equals(listing.ownerId())) {
            notificationService.notifyUser(listing.ownerId(), NotificationType.NEW_COMMENT,
                    "Bình luận mới", "Tin \"" + listing.title() + "\" vừa có bình luận mới.");
        }

        UserGateway.UserBrief author = userGateway.findBrief(userId).orElse(null);
        return buildSingle(comment, author, listing.ownerId(), userId, true);
    }

    @Override
    @Transactional
    public CommentResponse replyComment(Long parentCommentId, UpdateCommentRequest request, Long userId) {
        Comment parent = commentRepository.findByIdAndDeletedAtIsNull(parentCommentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND));
        CreateCommentRequest create = CreateCommentRequest.builder()
                .content(request.getContent())
                .parentCommentId(parentCommentId)
                .build();
        return createComment(parent.getListingId(), create, userId);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest request, Long userId) {
        Comment c = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND));
        if (!c.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.COMMENT_FORBIDDEN);
        }
        if (Instant.now().isAfter(editableUntil(c))) {
            throw new BusinessRuleException(ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED);
        }

        BannedKeywordGateway.ScanResult scan = bannedKeywordGateway.scan(request.getContent(), BannedKeywordScope.COMMENT);
        if (scan.isSevere()) {
            throw new BusinessRuleException(ErrorCode.BANNED_KEYWORD_DETECTED,
                    "Nội dung chứa từ ngữ không được phép: " + String.join(", ", scan.matched()));
        }
        boolean mild = scan.isMild();

        c.setContent(HtmlSanitizer.stripAllHtml(request.getContent()));
        c.setContainsBannedKeyword(mild);
        if (mild) {
            c.setStatus(CommentStatus.PENDING);
        }
        c.setEditedAt(Instant.now());
        commentRepository.save(c);
        syncCommentCount(c.getListingId());

        UserGateway.UserBrief author = userGateway.findBrief(userId).orElse(null);
        return buildSingle(c, author, listingOwnerId(c.getListingId()), userId, true);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment c = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND));
        boolean author = c.getUserId().equals(userId);
        boolean moderator = SecurityUtils.hasAnyRole(RoleCode.MODERATOR, RoleCode.ADMIN);
        if (!author && !moderator) {
            throw new ForbiddenException(ErrorCode.COMMENT_FORBIDDEN);
        }
        // Tác giả bị giới hạn cửa sổ; Moderator không.
        if (author && !moderator && Instant.now().isAfter(editableUntil(c))) {
            throw new BusinessRuleException(ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED);
        }

        c.setStatus(CommentStatus.DELETED);
        c.softDelete();
        commentRepository.save(c);

        if (c.getParent() != null) {
            Comment parent = c.getParent();
            parent.setReplyCount(Math.max(0, parent.getReplyCount() - 1));
            commentRepository.save(parent);
        }
        syncCommentCount(c.getListingId());
    }

    @Override
    @Transactional
    public void hideByModeration(Long commentId, Long moderatorId, String reason) {
        Comment c = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND));
        c.setStatus(CommentStatus.HIDDEN);
        c.setHiddenReason(trim(reason, 500));
        c.setHiddenBy(moderatorId);
        c.setHiddenAt(Instant.now());
        commentRepository.save(c);
        syncCommentCount(c.getListingId());
    }

    // ------------------ Kiểm duyệt (COMMENT_MODERATE) ------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminCommentResponse> adminListComments(CommentStatus status, Boolean isSpam,
                                                                String keyword, Long listingId,
                                                                Pageable pageable) {
        Specification<Comment> spec = CommentSpecifications.notDeleted();
        if (status != null) {
            spec = spec.and(CommentSpecifications.statusEq(status));
        }
        if (isSpam != null) {
            spec = spec.and(CommentSpecifications.spamEq(isSpam));
        }
        if (listingId != null) {
            spec = spec.and(CommentSpecifications.listingIdEq(listingId));
        }
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(CommentSpecifications.contentContains(keyword));
        }

        Page<Comment> page = commentRepository.findAll(spec, pageable);
        List<Long> authorIds = page.getContent().stream().map(Comment::getUserId).distinct().toList();
        Map<Long, UserGateway.UserBrief> authors = userGateway.getBriefs(authorIds);

        return PageResponse.from(page, c -> commentMapper.toAdminResponse(
                c, commentMapper.toAuthor(authors.get(c.getUserId()), null)));
    }

    @Override
    @Transactional
    public AdminCommentResponse adminHideComment(Long commentId, Long moderatorId, String reason) {
        Comment c = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND));
        if (c.getStatus() == CommentStatus.HIDDEN) {
            throw new ConflictException(ErrorCode.COMMENT_ALREADY_HIDDEN);
        }
        c.setStatus(CommentStatus.HIDDEN);
        c.setHiddenReason(trim(reason, 500));
        c.setHiddenBy(moderatorId);
        c.setHiddenAt(Instant.now());
        commentRepository.save(c);
        syncCommentCount(c.getListingId());

        auditLogService.record(AuditAction.COMMENT_HIDE, AuditActorType.USER, moderatorId,
                "COMMENT", commentId, trim(reason, 500));
        return buildAdmin(c);
    }

    @Override
    @Transactional
    public AdminCommentResponse unhideByModeration(Long commentId, Long moderatorId) {
        Comment c = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND));
        if (c.getStatus() != CommentStatus.HIDDEN) {
            throw new BusinessRuleException(ErrorCode.COMMENT_INVALID_TRANSITION);
        }
        c.setStatus(CommentStatus.VISIBLE);
        c.setHiddenReason(null);
        c.setHiddenBy(null);
        c.setHiddenAt(null);
        commentRepository.save(c);
        syncCommentCount(c.getListingId());

        auditLogService.record(AuditAction.COMMENT_UNHIDE, AuditActorType.USER, moderatorId,
                "COMMENT", commentId, "Khôi phục bình luận bị ẩn");
        return buildAdmin(c);
    }

    @Override
    @Transactional
    public AdminCommentResponse markSpam(Long commentId, Long moderatorId) {
        Comment c = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND));
        if (Boolean.TRUE.equals(c.getIsSpam())) {
            throw new ConflictException(ErrorCode.COMMENT_INVALID_TRANSITION);
        }
        c.setIsSpam(true);
        commentRepository.save(c);

        auditLogService.record(AuditAction.COMMENT_SPAM, AuditActorType.USER, moderatorId,
                "COMMENT", commentId, "Đánh dấu bình luận là spam");
        return buildAdmin(c);
    }

    // ------------------------------------------------------------------

    private AdminCommentResponse buildAdmin(Comment c) {
        AuthorResponse author = commentMapper.toAuthor(userGateway.findBrief(c.getUserId()).orElse(null), null);
        return commentMapper.toAdminResponse(c, author);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private Comment resolveParent(Long parentId, Long listingId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COMMENT_PARENT_NOT_FOUND));
        if (!parent.getListingId().equals(listingId)) {
            throw new BusinessRuleException(ErrorCode.COMMENT_PARENT_MISMATCH);
        }
        if (parent.getParent() != null) {
            throw new BusinessRuleException(ErrorCode.COMMENT_NESTING_TOO_DEEP);
        }
        if (parent.getStatus() != CommentStatus.VISIBLE) {
            throw new BusinessRuleException(ErrorCode.COMMENT_NOT_VISIBLE);
        }
        return parent;
    }

    private void syncCommentCount(Long listingId) {
        int count = (int) commentRepository
                .countByListingIdAndStatusAndDeletedAtIsNull(listingId, CommentStatus.VISIBLE);
        listingGateway.setCommentCount(listingId, count);
    }

    private Long listingOwnerId(Long listingId) {
        return listingGateway.findBrief(listingId).map(ListingGateway.ListingBrief::ownerId).orElse(null);
    }

    private Instant editableUntil(Comment c) {
        int windowMin = systemConfig.getInt(ConfigKey.COMMENT_EDIT_WINDOW_MINUTES);
        return c.getCreatedAt().plus(Duration.ofMinutes(windowMin));
    }

    private CommentResponse toResponse(Comment c, Map<Long, UserGateway.UserBrief> authors, Long ownerId,
                                       Long viewerId, boolean viewerModerates, List<CommentResponse> replies) {
        AuthorResponse author = commentMapper.toAuthor(authors.get(c.getUserId()), ownerId);
        boolean isAuthor = viewerId != null && viewerId.equals(c.getUserId());
        Instant until = editableUntil(c);
        boolean editable = isAuthor && c.getStatus() == CommentStatus.VISIBLE && Instant.now().isBefore(until);
        boolean deletable = isAuthor || viewerModerates;
        return commentMapper.toResponse(c, author, editable, editable ? until : null, deletable, replies);
    }

    private CommentResponse buildSingle(Comment c, UserGateway.UserBrief authorBrief, Long ownerId,
                                        Long viewerId, boolean deletable) {
        AuthorResponse author = commentMapper.toAuthor(authorBrief, ownerId);
        Instant until = editableUntil(c);
        boolean editable = viewerId != null && viewerId.equals(c.getUserId())
                && c.getStatus() == CommentStatus.VISIBLE && Instant.now().isBefore(until);
        return commentMapper.toResponse(c, author, editable, editable ? until : null, deletable, List.of());
    }
}
