package com.webtro.modules.moderation.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ModerationActionType;
import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.RateLimitException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.moderation.dto.request.AssignReportRequest;
import com.webtro.modules.moderation.dto.request.CreateReportRequest;
import com.webtro.modules.moderation.dto.request.CreateWarningRequest;
import com.webtro.modules.moderation.dto.request.ResolveGroupRequest;
import com.webtro.modules.moderation.dto.request.ResolveReportRequest;
import com.webtro.modules.moderation.dto.response.AdminModerationActionResponse;
import com.webtro.modules.moderation.dto.response.AdminReportGroupResponse;
import com.webtro.modules.moderation.dto.response.AdminReportItemResponse;
import com.webtro.modules.moderation.dto.response.AdminReportListResponse;
import com.webtro.modules.moderation.dto.response.AssignReportResponse;
import com.webtro.modules.moderation.dto.response.MyReportResponse;
import com.webtro.modules.moderation.dto.response.ReportDetailResponse;
import com.webtro.modules.moderation.dto.response.ReportResponse;
import com.webtro.modules.moderation.dto.response.ReportTargetGroupResponse;
import com.webtro.modules.moderation.dto.response.ReporterInfo;
import com.webtro.modules.moderation.dto.response.ResolveGroupResponse;
import com.webtro.modules.moderation.dto.response.ResolveReportResponse;
import com.webtro.modules.moderation.dto.response.WarningItemResponse;
import com.webtro.modules.moderation.dto.response.WarningResponse;
import com.webtro.modules.moderation.entity.ModerationAction;
import com.webtro.modules.moderation.entity.Report;
import com.webtro.modules.moderation.entity.ViolationWarning;
import com.webtro.modules.moderation.mapper.ModerationActionMapper;
import com.webtro.modules.moderation.mapper.ReportMapper;
import com.webtro.modules.moderation.mapper.WarningMapper;
import com.webtro.modules.moderation.repository.ModerationActionRepository;
import com.webtro.modules.moderation.repository.ReportRepository;
import com.webtro.modules.moderation.repository.ViolationWarningRepository;
import com.webtro.modules.moderation.service.ModerationService;
import com.webtro.modules.moderation.spi.AuditGateway;
import com.webtro.modules.moderation.spi.ContentModerationGateway;
import com.webtro.modules.moderation.spi.ListingModerationGateway;
import com.webtro.modules.moderation.spi.UserModerationGateway;
import com.webtro.modules.moderation.specification.ReportSpecifications;
import com.webtro.security.RateLimitService;
import com.webtro.security.SecurityUtils;
import com.webtro.storage.FileStorage;
import com.webtro.storage.StoredImage;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hiện thực nghiệp vụ kiểm duyệt (canonical 4.8.1–4.8.2, 4.16.1–4.16.8, {@code [§3.13][§5.3][§5.4][§10.8]}).
 *
 * <p>Các bất biến ép tại đây (DB không CHECK được):
 * <ul>
 *   <li>Báo cáo {@code RESOLVED}/{@code REJECTED} luôn có {@code resolved_by}, {@code resolved_at},
 *       {@code is_valid} (xem {@link #closeReport}).</li>
 *   <li>Ranh giới quyền: chọn {@code SEVERE_LOCK} mà không có {@code LISTING_LOCK} → 403.</li>
 *   <li>Ngưỡng tự gắn cờ {@code NEED_REVIEW} (5 report / 5 tài khoản / 24h) và ngưỡng khóa đăng tin
 *       (3 cảnh báo / 30 ngày) đọc từ {@code system_configs}, không hardcode.</li>
 *   <li>Hành động của hệ thống ({@code AUTO_HIDE_BY_SYSTEM}/gắn cờ) ghi {@code moderation_actions}
 *       với {@code is_system = true}, KHÔNG ghi {@code violation_warnings}, KHÔNG ghi audit
 *       (canonical mục 5.1).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final ReportRepository reportRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ViolationWarningRepository violationWarningRepository;
    private final ReportMapper reportMapper;
    private final WarningMapper warningMapper;
    private final ModerationActionMapper moderationActionMapper;
    private final com.webtro.modules.admin.service.SystemConfigService systemConfig;
    private final RateLimitService rateLimitService;
    private final com.webtro.modules.notification.service.NotificationService notificationService;
    private final com.webtro.modules.listing.service.TrustScoreService trustScoreService;
    private final ListingModerationGateway listingGateway;
    private final UserModerationGateway userGateway;
    private final ContentModerationGateway contentGateway;
    private final AuditGateway auditGateway;
    private final FileStorage fileStorage;

    // ==================================================================
    //  Tạo báo cáo (4.8.1)
    // ==================================================================

    @Override
    @Transactional
    public ReportResponse createReport(CreateReportRequest request, Long reporterId) {
        rateLimit(reporterId);
        guardNotAbusiveReporter(reporterId);

        if (request.getReason() == ReportReason.OTHER
                && (request.getDescription() == null || request.getDescription().isBlank())) {
            throw new BusinessException(ErrorCode.REPORT_DESCRIPTION_REQUIRED);
        }

        TargetInfo target = resolveTarget(request.getTargetType(), request.getTargetId());
        if (target.ownerId() != null && target.ownerId().equals(reporterId)) {
            throw new BusinessRuleException(ErrorCode.REPORT_SELF_FORBIDDEN);
        }

        String dedupKey = buildDedupKeyAndGuardDuplicate(
                reporterId, request.getTargetType(), request.getTargetId(), request.getReason());

        String description = request.getDescription() == null ? null
                : HtmlSanitizer.stripAllHtml(request.getDescription());
        String evidenceUrl = storeEvidence(request.getEvidenceImage());
        ReportSeverity severity = severityOf(request.getReason());

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .listingId(target.listingId())
                .reason(request.getReason())
                .description(description)
                .evidenceImageUrl(evidenceUrl)
                .status(ReportStatus.PENDING)
                .severity(severity)
                .dedupKey(dedupKey)
                .build();
        report = reportRepository.save(report);

        maybeAutoFlag(request.getTargetType(), request.getTargetId());

        return reportMapper.toReportResponse(report, target.title());
    }

    private void rateLimit(Long reporterId) {
        int max = systemConfig.getInt(ConfigKey.SPAM_REPORT_DAILY);
        try {
            rateLimitService.checkLimit("report", String.valueOf(reporterId), max, Duration.ofDays(1));
        } catch (RateLimitException e) {
            throw new RateLimitException(ErrorCode.REPORT_RATE_LIMIT, e.getRetryAfterSeconds());
        }
    }

    /** Hạn chế tài khoản báo cáo sai nhiều lần ({@code [§3.13]}, config {@code report.abuse.*}). */
    private void guardNotAbusiveReporter(Long reporterId) {
        int windowDays = systemConfig.getInt(ConfigKey.REPORT_ABUSE_WINDOW_DAYS);
        int limit = systemConfig.getInt(ConfigKey.REPORT_ABUSE_REJECTED_COUNT);
        Instant since = Instant.now().minus(Duration.ofDays(windowDays));
        long rejected = reportRepository.count(base()
                .and(ReportSpecifications.reporter(reporterId))
                .and(ReportSpecifications.statusEq(ReportStatus.REJECTED))
                .and(ReportSpecifications.createdFrom(since)));
        if (rejected >= limit) {
            throw new ForbiddenException(ErrorCode.REPORT_RESTRICTED_ABUSE);
        }
    }

    private String buildDedupKeyAndGuardDuplicate(Long reporterId, ReportTargetType type, Long targetId,
                                                  ReportReason reason) {
        String base = type.name() + ":" + targetId + ":" + reason.name();
        List<Report> prior = reportRepository.findAll(base()
                .and(ReportSpecifications.reporter(reporterId))
                .and(ReportSpecifications.target(type, targetId))
                .and(ReportSpecifications.reasonEq(reason)));
        boolean openExists = prior.stream().anyMatch(r ->
                r.getStatus() == ReportStatus.PENDING || r.getStatus() == ReportStatus.REVIEWING);
        if (openExists) {
            throw new ConflictException(ErrorCode.REPORT_DUPLICATE);
        }
        // Cho phép báo cáo lại sau khi report cũ đã RESOLVED/REJECTED nhưng vẫn tôn trọng unique
        // (reporter_id, dedup_key) permanent của DB: thêm hậu tố chu kỳ theo số report đã có.
        int cycle = prior.size();
        String key = cycle == 0 ? base : base + "#" + cycle;
        return key.length() > 80 ? key.substring(0, 80) : key;
    }

    private String storeEvidence(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        long maxBytes = (long) systemConfig.getInt(ConfigKey.LISTING_IMAGE_MAX_SIZE_MB) * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        try {
            StoredImage stored = fileStorage.storeImage(file, "reports");
            return stored.getOriginalUrl();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.EVIDENCE_IMAGE_INVALID);
        }
    }

    /** Ngưỡng tự gắn cờ NEED_REVIEW (chỉ áp dụng cho tin đăng) — canonical 4.8.1 quy tắc 3, {@code [§5.3]}. */
    private void maybeAutoFlag(ReportTargetType type, Long targetId) {
        if (type != ReportTargetType.LISTING) {
            return;
        }
        int reportThreshold = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_REPORT_COUNT);
        int distinctThreshold = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_DISTINCT_REPORTERS);
        int windowHours = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_WINDOW_HOURS);
        Instant since = Instant.now().minus(Duration.ofHours(windowHours));

        List<Report> recent = reportRepository.findAll(base()
                .and(ReportSpecifications.target(type, targetId))
                .and(ReportSpecifications.statusEq(ReportStatus.PENDING))
                .and(ReportSpecifications.createdFrom(since)));
        long count = recent.size();
        long distinct = recent.stream().map(Report::getReporterId).distinct().count();

        if (count >= reportThreshold && distinct >= distinctThreshold) {
            String reason = "Tự động: " + count + " báo cáo từ " + distinct
                    + " tài khoản khác nhau trong " + windowHours + " giờ";
            listingGateway.flagNeedReview(targetId, reason);
            // Hành động hệ thống: ghi moderation_actions is_system=true, KHÔNG warning, KHÔNG audit.
            recordAction(ModerationActionType.FLAG_NEED_REVIEW, type, targetId, targetId, null, null,
                    true, null, reason, null, null, ListingStatus.NEED_REVIEW.name());
            notificationService.notifyModerators(NotificationType.REPORT_THRESHOLD,
                    "Tin bị báo cáo vượt ngưỡng",
                    reason + " — tin #" + targetId + " đã được gắn cờ cần kiểm tra.",
                    "/admin/reports/target/LISTING/" + targetId);
        }
    }

    // ==================================================================
    //  Báo cáo của tôi (4.8.2)
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyReportResponse> getMyReports(Long reporterId, List<ReportStatus> statuses,
                                                       ReportTargetType targetType, Pageable pageable) {
        Specification<Report> spec = base().and(ReportSpecifications.reporter(reporterId));
        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and(ReportSpecifications.statusesIn(statuses));
        }
        if (targetType != null) {
            spec = spec.and(ReportSpecifications.targetTypeEq(targetType));
        }
        Page<Report> page = reportRepository.findAll(spec, pageable);
        return PageResponse.from(page, r -> {
            TargetView view = describeTarget(r.getTargetType(), r.getTargetId());
            ModerationResult result = latestResultOf(r.getId());
            return reportMapper.toMyReport(r, view.title(), view.url(), result);
        });
    }

    // ==================================================================
    //  Danh sách quản trị (4.16.1)
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminReportListResponse adminListReports(List<ReportStatus> statuses, List<ReportTargetType> targetTypes,
                                                    Long targetId, List<ReportSeverity> severities, Long reporterId,
                                                    Instant from, Instant to, Pageable pageable) {
        List<ReportStatus> effectiveStatuses = (statuses == null || statuses.isEmpty())
                ? List.of(ReportStatus.PENDING, ReportStatus.REVIEWING) : statuses;

        Specification<Report> commonNoStatus = base();
        if (targetTypes != null && !targetTypes.isEmpty()) {
            commonNoStatus = commonNoStatus.and(ReportSpecifications.targetTypesIn(targetTypes));
        }
        if (targetId != null) {
            commonNoStatus = commonNoStatus.and(ReportSpecifications.targetIdEq(targetId));
        }
        if (reporterId != null) {
            commonNoStatus = commonNoStatus.and(ReportSpecifications.reporter(reporterId));
        }
        if (from != null) {
            commonNoStatus = commonNoStatus.and(ReportSpecifications.createdFrom(from));
        }
        if (to != null) {
            commonNoStatus = commonNoStatus.and(ReportSpecifications.createdTo(to));
        }

        Specification<Report> listSpec = commonNoStatus.and(ReportSpecifications.statusesIn(effectiveStatuses));
        if (severities != null && !severities.isEmpty()) {
            listSpec = listSpec.and(ReportSpecifications.severitiesIn(severities));
        }

        Page<Report> page = reportRepository.findAll(listSpec, pageable);

        Map<Long, ReporterInfo> reporterCache = new HashMap<>();
        PageResponse<AdminReportItemResponse> mapped = PageResponse.from(page, r -> {
            TargetView view = describeTarget(r.getTargetType(), r.getTargetId());
            ReporterInfo reporter = reporterCache.computeIfAbsent(r.getReporterId(),
                    id -> buildReporterInfo(id, false));
            long related = reportRepository.count(base().and(ReportSpecifications.target(r.getTargetType(), r.getTargetId())));
            ModerationAction action = latestActionOf(r.getId());
            ModerationResult result = action == null ? null : action.getResult();
            String note = action == null ? null : action.getNote();
            return reportMapper.toAdminItem(r, view.title(), view.ownerId(), view.ownerName(),
                    view.ownerTrustScore(), reporter, related, result, note);
        });

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ReportStatus st : ReportStatus.values()) {
            Specification<Report> s = commonNoStatus.and(ReportSpecifications.statusEq(st));
            if (severities != null && !severities.isEmpty()) {
                s = s.and(ReportSpecifications.severitiesIn(severities));
            }
            statusCounts.put(st.name(), reportRepository.count(s));
        }
        Map<String, Long> severityCounts = new LinkedHashMap<>();
        for (ReportSeverity sev : ReportSeverity.values()) {
            Specification<Report> s = commonNoStatus
                    .and(ReportSpecifications.statusesIn(effectiveStatuses))
                    .and(ReportSpecifications.severityEq(sev));
            severityCounts.put(sev.name(), reportRepository.count(s));
        }

        return AdminReportListResponse.builder()
                .page(mapped)
                .statusCounts(statusCounts)
                .severityCounts(severityCounts)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReportGroupResponse> adminListReportGroups(List<ReportStatus> statuses,
                                                                        List<ReportTargetType> targetTypes,
                                                                        List<ReportSeverity> severities,
                                                                        Instant from, Instant to, Pageable pageable) {
        Specification<Report> spec = base();
        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and(ReportSpecifications.statusesIn(statuses));
        }
        if (targetTypes != null && !targetTypes.isEmpty()) {
            spec = spec.and(ReportSpecifications.targetTypesIn(targetTypes));
        }
        if (severities != null && !severities.isEmpty()) {
            spec = spec.and(ReportSpecifications.severitiesIn(severities));
        }
        if (from != null) {
            spec = spec.and(ReportSpecifications.createdFrom(from));
        }
        if (to != null) {
            spec = spec.and(ReportSpecifications.createdTo(to));
        }

        // Gom nhóm trong bộ nhớ theo (targetType, targetId). Tập report đang mở thường không lớn.
        List<Report> all = reportRepository.findAll(spec);
        Map<String, List<Report>> groups = all.stream()
                .collect(Collectors.groupingBy(r -> r.getTargetType().name() + ":" + r.getTargetId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<AdminReportGroupResponse> rows = groups.values().stream()
                .map(this::toGroupRow)
                .sorted(Comparator.comparing(AdminReportGroupResponse::getLastReportedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int total = rows.size();
        int fromIdx = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), total);
        int toIdx = (int) Math.min((long) fromIdx + pageable.getPageSize(), total);
        List<AdminReportGroupResponse> pageRows = rows.subList(fromIdx, toIdx);
        int totalPages = pageable.getPageSize() == 0 ? 0
                : (int) Math.ceil((double) total / pageable.getPageSize());

        return PageResponse.<AdminReportGroupResponse>builder()
                .items(pageRows)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(total)
                .totalPages(totalPages)
                .first(pageable.getPageNumber() == 0)
                .last(toIdx >= total)
                .build();
    }

    private AdminReportGroupResponse toGroupRow(List<Report> group) {
        Report sample = group.get(0);
        TargetView view = describeTarget(sample.getTargetType(), sample.getTargetId());
        long distinct = group.stream().map(Report::getReporterId).distinct().count();
        ReportSeverity maxSeverity = group.stream().map(Report::getSeverity)
                .max(Comparator.comparingInt(Enum::ordinal)).orElse(ReportSeverity.LOW);
        Map<String, Long> byReason = group.stream()
                .collect(Collectors.groupingBy(r -> r.getReason().name(), Collectors.counting()));
        List<Long> ids = group.stream().map(Report::getId).toList();
        long pending = group.stream().filter(r -> r.getStatus() == ReportStatus.PENDING
                || r.getStatus() == ReportStatus.REVIEWING).count();
        Instant first = group.stream().map(Report::getCreatedAt).min(Comparator.naturalOrder()).orElse(null);
        Instant last = group.stream().map(Report::getCreatedAt).max(Comparator.naturalOrder()).orElse(null);

        return AdminReportGroupResponse.builder()
                .targetType(sample.getTargetType().name())
                .targetId(sample.getTargetId())
                .targetTitle(view.title())
                .targetStatus(view.status())
                .targetOwnerId(view.ownerId())
                .targetOwnerName(view.ownerName())
                .targetOwnerTrustScore(view.ownerTrustScore())
                .reportCount(group.size())
                .distinctReporterCount(distinct)
                .maxSeverity(maxSeverity)
                .reportsByReason(byReason)
                .reportIds(ids)
                .pendingCount(pending)
                .firstReportedAt(first)
                .lastReportedAt(last)
                .build();
    }

    // ==================================================================
    //  Chi tiết báo cáo (4.16.2)
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public ReportDetailResponse getReportDetail(Long reportId) {
        Report report = getAliveReport(reportId);
        TargetView view = describeTarget(report.getTargetType(), report.getTargetId());

        List<Report> allForTarget = reportRepository.findAll(base()
                .and(ReportSpecifications.target(report.getTargetType(), report.getTargetId())));
        long distinct = allForTarget.stream().map(Report::getReporterId).distinct().count();

        List<ReportDetailResponse.RelatedReport> related = allForTarget.stream()
                .filter(r -> !r.getId().equals(reportId))
                .sorted(Comparator.comparing(Report::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(r -> ReportDetailResponse.RelatedReport.builder()
                        .id(r.getId()).reason(r.getReason()).severity(r.getSeverity())
                        .reporterId(r.getReporterId()).description(r.getDescription())
                        .status(r.getStatus()).createdAt(r.getCreatedAt()).build())
                .toList();

        Page<ModerationAction> history = moderationActionRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDesc(report.getTargetType(),
                        report.getTargetId(), PageRequest.of(0, 20));
        List<ReportDetailResponse.ModerationHistoryEntry> historyEntries = history.getContent().stream()
                .map(a -> ReportDetailResponse.ModerationHistoryEntry.builder()
                        .id(a.getId()).type(a.getActionType().name()).reason(a.getReason())
                        .moderatorId(a.getModeratorId()).system(Boolean.TRUE.equals(a.getIsSystem()))
                        .createdAt(a.getCreatedAt()).build())
                .toList();

        ModerationAction latest = latestActionOf(reportId);

        ReportDetailResponse.TargetContext target = ReportDetailResponse.TargetContext.builder()
                .type(report.getTargetType().name())
                .id(report.getTargetId())
                .title(view.title())
                .status(view.status())
                .excerpt(view.excerpt())
                .ownerId(view.ownerId())
                .ownerName(view.ownerName())
                .ownerTrustScore(view.ownerTrustScore())
                .ownerWarningCountLast30Days(view.ownerId() == null ? null : countActiveWarnings(view.ownerId()))
                .ownerLockedListingCountLast60Days(view.ownerId() == null ? null : countRecentLockedListings(view.ownerId()))
                .build();

        Recommendation rec = recommend(report, allForTarget, distinct, view);

        return ReportDetailResponse.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .reasonLabel(report.getReason().getLabel())
                .description(report.getDescription())
                .evidenceImageUrl(report.getEvidenceImageUrl())
                .status(report.getStatus())
                .severity(report.getSeverity())
                .result(latest == null ? null : latest.getResult())
                .internalNote(latest == null ? null : latest.getNote())
                .assignedToId(report.getResolvedBy())
                .reporter(buildReporterInfo(report.getReporterId(), true))
                .target(target)
                .relatedReports(related)
                .relatedReportCount(allForTarget.size())
                .distinctReporterCount(distinct)
                .moderationHistory(historyEntries)
                .recommendedResult(rec.result())
                .recommendationBasis(rec.basis())
                .createdAt(report.getCreatedAt())
                .resolvedAt(report.getResolvedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminModerationActionResponse> adminListActions(ReportTargetType targetType,
                                                                       Long listingId, Pageable pageable) {
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        Page<ModerationAction> page = moderationActionRepository.searchRecent(targetType, listingId, effective);
        return PageResponse.from(page, moderationActionMapper::toResponse);
    }

    private record Recommendation(ModerationResult result, List<String> basis) {
    }

    /** Đề xuất kết quả (heuristic từ số liệu report + số tin bị khóa). Chỉ là gợi ý (canonical 4.16.2). */
    private Recommendation recommend(Report report, List<Report> allForTarget, long distinct, TargetView view) {
        List<String> basis = new ArrayList<>();
        int reportThreshold = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_REPORT_COUNT);
        int distinctThreshold = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_DISTINCT_REPORTERS);
        long criticalCount = allForTarget.stream().filter(r -> r.getSeverity() == ReportSeverity.CRITICAL).count();

        ModerationResult result = ModerationResult.NO_VIOLATION;
        if (allForTarget.size() >= reportThreshold && distinct >= distinctThreshold) {
            basis.add(allForTarget.size() + " báo cáo từ " + distinct + " tài khoản khác nhau (ngưỡng "
                    + reportThreshold + "/" + distinctThreshold + ")");
            result = ModerationResult.MEDIUM_HIDE;
        }
        if (criticalCount > 0) {
            basis.add("Có " + criticalCount + " báo cáo mức CRITICAL");
            result = ModerationResult.SEVERE_LOCK;
        }
        if (view.ownerId() != null) {
            long locked = countRecentLockedListings(view.ownerId());
            int lockedThreshold = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_LOCKED_LISTING_COUNT);
            if (locked >= lockedThreshold) {
                basis.add("Chủ đã có " + locked + " tin bị khóa gần đây (ngưỡng " + lockedThreshold + ")");
                result = ModerationResult.SEVERE_LOCK;
            }
        }
        if (basis.isEmpty()) {
            basis.add("Chưa đủ tín hiệu vi phạm rõ ràng — cần xác minh thủ công");
        }
        return new Recommendation(result, basis);
    }

    // ==================================================================
    //  Nhận xử lý (4.16.3)
    // ==================================================================

    @Override
    @Transactional
    public AssignReportResponse assignReport(Long reportId, AssignReportRequest request, Long moderatorId) {
        if (request.getStatus() != ReportStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ nhận xử lý (REVIEWING) qua endpoint này");
        }
        Report report = getAliveReport(reportId);
        if (isTerminal(report.getStatus())) {
            throw new ConflictException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        ReportStatus previous = report.getStatus();
        report.setStatus(ReportStatus.REVIEWING);
        report.setResolvedBy(moderatorId);
        reportRepository.save(report);

        // Gán luôn mọi report cùng đối tượng đang chờ ({@code [§3.13]} "gom nhóm để xử lý").
        int alsoAssigned = 0;
        List<Report> related = reportRepository.findAll(base()
                .and(ReportSpecifications.target(report.getTargetType(), report.getTargetId()))
                .and(ReportSpecifications.statusEq(ReportStatus.PENDING)));
        for (Report r : related) {
            if (!r.getId().equals(report.getId())) {
                r.setStatus(ReportStatus.REVIEWING);
                r.setResolvedBy(moderatorId);
                reportRepository.save(r);
                alsoAssigned++;
            }
        }

        String moderatorName = userGateway.findRef(moderatorId).map(UserModerationGateway.UserRef::fullName).orElse(null);
        return AssignReportResponse.builder()
                .id(report.getId())
                .status(ReportStatus.REVIEWING)
                .previousStatus(previous)
                .assignedToId(moderatorId)
                .assignedToName(moderatorName)
                .relatedReportsAlsoAssigned(alsoAssigned)
                .assignedAt(Instant.now())
                .build();
    }

    // ==================================================================
    //  Xử lý một báo cáo (4.16.4)
    // ==================================================================

    @Override
    @Transactional
    public ResolveReportResponse resolveReport(Long reportId, ResolveReportRequest request, Long moderatorId) {
        Report report = getAliveReport(reportId);
        if (isTerminal(report.getStatus())) {
            throw new ConflictException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        ModerationResult result = request.getResult();
        boolean needWarning = result != ModerationResult.NO_VIOLATION;
        if (needWarning && (request.getWarningMessage() == null || request.getWarningMessage().isBlank())) {
            throw new BusinessException(ErrorCode.WARNING_REASON_REQUIRED);
        }
        guardLockPermission(result);

        TargetInfo target = resolveTarget(report.getTargetType(), report.getTargetId());
        String moderatorResponse = sanitize(request.getModeratorResponse());
        String warningMessage = sanitize(request.getWarningMessage());
        String internalNote = sanitize(request.getInternalNote());

        TargetOutcome outcome = executeResolution(result, report.getTargetType(), report.getTargetId(),
                target.listingId(), target.ownerId(), moderatorId, report.getSeverity(),
                warningMessage, internalNote, report.getId());

        ReportStatus previous = report.getStatus();
        closeReport(report, result, moderatorId, moderatorResponse);

        int relatedResolved = 0;
        if (Boolean.TRUE.equals(request.getResolveRelated())) {
            List<Report> related = reportRepository.findAll(base()
                    .and(ReportSpecifications.target(report.getTargetType(), report.getTargetId()))
                    .and(ReportSpecifications.statusesIn(List.of(ReportStatus.PENDING, ReportStatus.REVIEWING))));
            for (Report r : related) {
                if (!r.getId().equals(report.getId())) {
                    closeReport(r, result, moderatorId, moderatorResponse);
                    relatedResolved++;
                }
            }
        }

        recalcTrust(target);

        Long ownerWarn30 = target.ownerId() == null ? null : countActiveWarnings(target.ownerId());
        Long ownerLocked60 = target.ownerId() == null ? null : countRecentLockedListings(target.ownerId());
        boolean suspensionTriggered = maybeSuspendPosting(target.ownerId(), outcome.warningIssued(), ownerWarn30);
        boolean accountLockSuggested = ownerLocked60 != null
                && ownerLocked60 >= systemConfig.getInt(ConfigKey.MOD_THRESHOLD_LOCKED_LISTING_COUNT);

        Long auditId = writeLockAuditIfNeeded(result, report.getTargetType(), report.getTargetId(),
                view(target), moderatorId, warningMessage);

        String moderatorName = userGateway.findRef(moderatorId)
                .map(UserModerationGateway.UserRef::fullName).orElse(null);

        return ResolveReportResponse.builder()
                .id(report.getId())
                .status(report.getStatus())
                .previousStatus(previous)
                .result(result)
                .resultLabel(result.getLabel())
                .moderatorResponse(moderatorResponse)
                .warningMessage(warningMessage)
                .resolvedById(moderatorId)
                .resolvedByName(moderatorName)
                .targetAction(outcome.targetAction())
                .warningIssued(outcome.warningIssued())
                .warningId(outcome.warningId())
                .ownerWarningCountLast30Days(ownerWarn30)
                .ownerLockedListingCountLast60Days(ownerLocked60)
                .accountLockSuggested(accountLockSuggested)
                .accountLockSuggestionReason(accountLockSuggested
                        ? "Chủ đã có " + ownerLocked60 + " tin bị khóa trong "
                        + systemConfig.getInt(ConfigKey.MOD_THRESHOLD_LOCKED_LISTING_WINDOW_DAYS) + " ngày" : null)
                .relatedReportsResolved(relatedResolved)
                .reporterNotified(moderatorResponse != null)
                .ownerNotified(outcome.warningIssued())
                .moderationActionId(outcome.moderationActionId())
                .auditLogId(auditId)
                .resolvedAt(report.getResolvedAt())
                .build();
    }

    // ==================================================================
    //  Toàn bộ báo cáo về một đối tượng (4.16.7)
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public ReportTargetGroupResponse getReportsByTarget(ReportTargetType targetType, Long targetId,
                                                        List<ReportStatus> statuses, Pageable pageable) {
        TargetView view = describeTargetOrThrow(targetType, targetId);

        List<Report> all = reportRepository.findAll(base().and(ReportSpecifications.target(targetType, targetId)));
        if (all.isEmpty()) {
            // Đối tượng tồn tại nhưng chưa có báo cáo -> trả nhóm rỗng (không lỗi).
            return ReportTargetGroupResponse.builder()
                    .target(targetSnapshot(view, targetType, targetId))
                    .summary(emptySummary())
                    .reports(PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize()))
                    .build();
        }

        Specification<Report> spec = base().and(ReportSpecifications.target(targetType, targetId));
        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and(ReportSpecifications.statusesIn(statuses));
        }
        Page<Report> page = reportRepository.findAll(spec, pageable);

        Map<Long, ReporterInfo> reporterCache = new HashMap<>();
        PageResponse<ReportTargetGroupResponse.TargetReportItem> items = PageResponse.from(page, r -> {
            ReporterInfo ri = reporterCache.computeIfAbsent(r.getReporterId(), id -> buildReporterInfo(id, false));
            return ReportTargetGroupResponse.TargetReportItem.builder()
                    .id(r.getId()).reporterId(r.getReporterId()).reporterName(ri.getFullName())
                    .reporterReportCount(ri.getReportCount()).reporterRejectedCount(ri.getRejectedReportCount())
                    .reason(r.getReason()).severity(r.getSeverity()).description(r.getDescription())
                    .status(r.getStatus()).assignedToId(r.getResolvedBy()).createdAt(r.getCreatedAt())
                    .build();
        });

        return ReportTargetGroupResponse.builder()
                .target(targetSnapshot(view, targetType, targetId))
                .summary(summarize(all, targetType))
                .reports(items)
                .build();
    }

    private ReportTargetGroupResponse.GroupSummary emptySummary() {
        return ReportTargetGroupResponse.GroupSummary.builder()
                .totalCount(0).pendingCount(0).reviewingCount(0).resolvedCount(0).rejectedCount(0)
                .distinctReporterCount(0).reasonBreakdown(List.of()).autoHideThresholdMet(false)
                .build();
    }

    private ReportTargetGroupResponse.GroupSummary summarize(List<Report> all, ReportTargetType targetType) {
        long distinct = all.stream().map(Report::getReporterId).distinct().count();
        ReportSeverity highest = all.stream().map(Report::getSeverity)
                .max(Comparator.comparingInt(Enum::ordinal)).orElse(ReportSeverity.LOW);
        List<ReportTargetGroupResponse.ReasonCount> breakdown = all.stream()
                .collect(Collectors.groupingBy(Report::getReason, Collectors.counting()))
                .entrySet().stream()
                .map(e -> ReportTargetGroupResponse.ReasonCount.builder().reason(e.getKey()).count(e.getValue()).build())
                .toList();

        int distinctThreshold = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_DISTINCT_REPORTERS);
        int reportThreshold = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_REPORT_COUNT);
        int windowHours = systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_WINDOW_HOURS);
        Instant since = Instant.now().minus(Duration.ofHours(windowHours));
        List<Report> inWindow = all.stream().filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(since))
                .filter(r -> r.getStatus() == ReportStatus.PENDING).toList();
        long distinctInWindow = inWindow.stream().map(Report::getReporterId).distinct().count();
        boolean met = targetType == ReportTargetType.LISTING
                && inWindow.size() >= reportThreshold && distinctInWindow >= distinctThreshold;

        return ReportTargetGroupResponse.GroupSummary.builder()
                .totalCount(all.size())
                .pendingCount(countStatus(all, ReportStatus.PENDING))
                .reviewingCount(countStatus(all, ReportStatus.REVIEWING))
                .resolvedCount(countStatus(all, ReportStatus.RESOLVED))
                .rejectedCount(countStatus(all, ReportStatus.REJECTED))
                .distinctReporterCount(distinct)
                .highestSeverity(highest)
                .firstReportedAt(all.stream().map(Report::getCreatedAt).min(Comparator.naturalOrder()).orElse(null))
                .lastReportedAt(all.stream().map(Report::getCreatedAt).max(Comparator.naturalOrder()).orElse(null))
                .reasonBreakdown(breakdown)
                .autoHideThresholdMet(met)
                .autoHideThresholdDetail(met ? distinctInWindow + " người báo cáo khác nhau trong "
                        + windowHours + " giờ ≥ ngưỡng " + distinctThreshold : null)
                .build();
    }

    private long countStatus(List<Report> reports, ReportStatus status) {
        return reports.stream().filter(r -> r.getStatus() == status).count();
    }

    private ReportTargetGroupResponse.TargetSnapshot targetSnapshot(TargetView view, ReportTargetType type, Long id) {
        return ReportTargetGroupResponse.TargetSnapshot.builder()
                .type(type.name()).id(id).title(view.title()).status(view.status()).excerpt(view.excerpt())
                .ownerId(view.ownerId()).ownerName(view.ownerName()).ownerTrustScore(view.ownerTrustScore())
                .ownerWarningCountLast30Days(view.ownerId() == null ? null : countActiveWarnings(view.ownerId()))
                .build();
    }

    // ==================================================================
    //  Xử lý cả nhóm (4.16.8)
    // ==================================================================

    @Override
    @Transactional
    public ResolveGroupResponse resolveGroup(ResolveGroupRequest request, Long moderatorId) {
        // Kiểm quyền TRƯỚC khi đóng bất kỳ report nào (ADR-10: bulk không là cửa sau vượt phân quyền).
        if (request.getResult() == ModerationResult.SEVERE_LOCK
                && !SecurityUtils.hasRole(RoleCode.ADMIN)) {
            throw new ForbiddenException(ErrorCode.REPORT_RESULT_FORBIDDEN);
        }
        TargetInfo target = resolveTargetOrThrow(request.getTargetType(), request.getTargetId());

        List<Report> allForTarget = reportRepository.findAll(base()
                .and(ReportSpecifications.target(request.getTargetType(), request.getTargetId())));
        boolean onlyPending = request.getOnlyPending() == null || request.getOnlyPending();

        List<Report> toClose = allForTarget.stream()
                .filter(r -> !onlyPending || !isTerminal(r.getStatus()))
                .toList();
        if (toClose.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.REPORT_GROUP_EMPTY);
        }

        ModerationResult result = request.getResult();
        String moderatorResponse = sanitize(request.getModeratorResponse());
        String internalNote = sanitize(request.getInternalNote());
        ReportSeverity severity = toClose.stream().map(Report::getSeverity)
                .max(Comparator.comparingInt(Enum::ordinal)).orElse(ReportSeverity.MEDIUM);

        // Hành động trên đối tượng chạy đúng một lần.
        TargetOutcome outcome = executeResolution(result, request.getTargetType(), request.getTargetId(),
                target.listingId(), target.ownerId(), moderatorId, severity, moderatorResponse, internalNote, null);

        List<Long> resolvedIds = new ArrayList<>();
        for (Report r : toClose) {
            closeReport(r, result, moderatorId, moderatorResponse);
            resolvedIds.add(r.getId());
        }

        List<ResolveGroupResponse.Skipped> skipped = allForTarget.stream()
                .filter(r -> onlyPending && isTerminal(r.getStatus()))
                .map(r -> ResolveGroupResponse.Skipped.builder()
                        .id(r.getId())
                        .reason("Đã ở trạng thái " + r.getStatus().name() + " trước đó (onlyPending = true)")
                        .build())
                .toList();

        recalcTrust(target);

        int reportersNotified = (int) toClose.stream().map(Report::getReporterId).distinct().count();
        Long auditId = writeLockAuditIfNeeded(result, request.getTargetType(), request.getTargetId(),
                view(target), moderatorId, moderatorResponse);

        return ResolveGroupResponse.builder()
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .result(result)
                .resolvedReportIds(resolvedIds)
                .resolvedCount(resolvedIds.size())
                .skippedCount(skipped.size())
                .skipped(skipped)
                .targetAction(outcome.targetAction())
                .warningIssued(outcome.warningIssued())
                .warningId(outcome.warningId())
                .reportersNotified(reportersNotified)
                .ownerNotified(outcome.warningIssued())
                .auditLogIds(auditId == null ? List.of() : List.of(auditId))
                .resolvedById(moderatorId)
                .resolvedAt(Instant.now())
                .build();
    }

    // ==================================================================
    //  Cảnh báo (4.16.5, 4.16.6)
    // ==================================================================

    @Override
    @Transactional
    public WarningResponse createWarning(CreateWarningRequest request, Long issuerId) {
        UserModerationGateway.UserRef user = userGateway.findRef(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (user.isAdmin()) {
            throw new ForbiddenException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
        if (request.getUserId().equals(issuerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Không thể tự gửi cảnh báo cho chính mình");
        }
        if (request.getRelatedListingId() != null) {
            ListingModerationGateway.ListingRef ref = listingGateway.findRef(request.getRelatedListingId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
            if (!request.getUserId().equals(ref.ownerId())) {
                throw new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND);
            }
        }
        if (request.getRelatedReportId() != null) {
            reportRepository.findByIdAndDeletedAtIsNull(request.getRelatedReportId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_NOT_FOUND));
        }

        String message = sanitize(request.getReason());
        ModerationAction action = recordAction(ModerationActionType.WARN, ReportTargetType.USER,
                request.getUserId(), request.getRelatedListingId(), request.getRelatedReportId(),
                issuerId, false, null, message, null, null, null);
        ViolationWarning warning = issueWarning(request.getUserId(), request.getRelatedListingId(),
                request.getRelatedReportId(), action.getId(), request.getSeverity(), message, issuerId, false);

        long warn30 = countActiveWarnings(request.getUserId());
        int warnThreshold = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_WARNING_COUNT);
        Instant suspendedUntil = null;
        boolean suspension = false;
        if (warn30 >= warnThreshold) {
            int days = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_WARNING_WINDOW_DAYS);
            suspendedUntil = Instant.now().plus(Duration.ofDays(days));
            userGateway.suspendPosting(request.getUserId(), suspendedUntil,
                    "Vượt ngưỡng " + warnThreshold + " cảnh báo trong " + days + " ngày");
            suspension = true;
        }
        long locked60 = countRecentLockedListings(request.getUserId());
        boolean accountLockSuggested = locked60 >= systemConfig.getInt(ConfigKey.MOD_THRESHOLD_LOCKED_LISTING_COUNT);

        notificationService.notifyUser(request.getUserId(), NotificationType.VIOLATION_WARNING,
                "Bạn nhận được một cảnh báo vi phạm", message, "/tai-khoan/canh-bao", null);

        String issuerName = userGateway.findRef(issuerId).map(UserModerationGateway.UserRef::fullName).orElse(null);
        return WarningResponse.builder()
                .id(warning.getId())
                .userId(user.id())
                .userName(user.fullName())
                .reason(message)
                .severity(request.getSeverity())
                .relatedListingId(request.getRelatedListingId())
                .relatedReportId(request.getRelatedReportId())
                .issuedById(issuerId)
                .issuedByName(issuerName)
                .warningCountLast30Days(warn30)
                .warningThreshold(warnThreshold)
                .postingSuspensionTriggered(suspension)
                .postingSuspendedUntil(suspendedUntil)
                .accountLockSuggested(accountLockSuggested)
                .userNotified(true)
                .emailSent(Boolean.TRUE.equals(request.getNotifyByEmail()))
                .createdAt(warning.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarningItemResponse> listWarnings(Long userId, List<ReportSeverity> severities,
                                                          Long issuedById, boolean activeOnly,
                                                          Instant from, Instant to, Pageable pageable) {
        // ViolationWarningRepository (đã chốt) không có Specification; lọc theo userId dùng finder có
        // sẵn, các bộ lọc phụ (severity/issuedBy/ngày) áp trong bộ nhớ trên trang. Xem "điểm quyết định".
        Page<ViolationWarning> page = userId != null
                ? violationWarningRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : violationWarningRepository.findAll(pageable);

        int windowDays = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_WARNING_WINDOW_DAYS);
        Instant windowStart = Instant.now().minus(Duration.ofDays(windowDays));

        List<WarningItemResponse> items = page.getContent().stream()
                .filter(w -> severities == null || severities.isEmpty() || severities.contains(w.getSeverity()))
                .filter(w -> issuedById == null || issuedById.equals(w.getIssuedBy()))
                .filter(w -> from == null || (w.getCreatedAt() != null && !w.getCreatedAt().isBefore(from)))
                .filter(w -> to == null || (w.getCreatedAt() != null && !w.getCreatedAt().isAfter(to)))
                .filter(w -> !activeOnly || (w.getCreatedAt() != null && !w.getCreatedAt().isBefore(windowStart)))
                .map(w -> warningMapper.toItem(w,
                        userGateway.findRef(w.getUserId()).map(UserModerationGateway.UserRef::fullName).orElse(null),
                        w.getListingId() == null ? null
                                : listingGateway.findRef(w.getListingId()).map(ListingModerationGateway.ListingRef::title).orElse(null),
                        w.getIssuedBy() == null ? null
                                : userGateway.findRef(w.getIssuedBy()).map(UserModerationGateway.UserRef::fullName).orElse(null),
                        windowStart))
                .toList();

        return PageResponse.<WarningItemResponse>builder()
                .items(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    // ==================================================================
    //  Đếm ngưỡng (§5.4)
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public long countActiveWarnings(Long userId) {
        int windowDays = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_WARNING_WINDOW_DAYS);
        Instant since = Instant.now().minus(Duration.ofDays(windowDays));
        return violationWarningRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                .stream()
                .filter(w -> w.getCreatedAt() != null && !w.getCreatedAt().isBefore(since))
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countRecentLockedListings(Long ownerId) {
        int windowDays = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_LOCKED_LISTING_WINDOW_DAYS);
        Instant since = Instant.now().minus(Duration.ofDays(windowDays));
        return listingGateway.countLockedListingsSince(ownerId, since);
    }

    // ==================================================================
    //  Thực thi kết luận trên đối tượng (dùng chung 4.16.4 + 4.16.8)
    // ==================================================================

    private record TargetOutcome(ResolveReportResponse.TargetAction targetAction, Long moderationActionId,
                                 boolean warningIssued, Long warningId) {
    }

    private TargetOutcome executeResolution(ModerationResult result, ReportTargetType targetType, Long targetId,
                                            Long listingId, Long ownerId, Long moderatorId, ReportSeverity severity,
                                            String warningMessage, String internalNote, Long reportId) {
        ModerationActionType actionType;
        ResolveReportResponse.TargetAction targetAction = null;
        String actionReason = warningMessage != null ? warningMessage : result.getLabel();

        switch (result) {
            case NO_VIOLATION -> {
                actionType = ModerationActionType.DISMISS;
                if (targetType == ReportTargetType.LISTING) {
                    listingGateway.clearNeedReview(targetId, moderatorId, "Kết luận không vi phạm");
                }
            }
            case MINOR_WARN -> actionType = ModerationActionType.WARN;
            case MEDIUM_HIDE -> {
                actionType = ModerationActionType.HIDE;
                targetAction = hideTarget(targetType, targetId, listingId, moderatorId, actionReason);
            }
            case SEVERE_LOCK -> {
                if (targetType == ReportTargetType.LISTING) {
                    actionType = ModerationActionType.LOCK;
                    ListingStatus prev = listingGateway.lock(targetId, moderatorId, severity, actionReason);
                    targetAction = ResolveReportResponse.TargetAction.builder()
                            .type("LISTING_LOCKED").targetId(targetId)
                            .previousStatus(prev == null ? null : prev.name())
                            .newStatus(ListingStatus.LOCKED.name()).severity(severity.name()).build();
                    notificationService.notifyUser(ownerId, NotificationType.LISTING_LOCKED,
                            "Tin đăng của bạn đã bị khóa", actionReason, "/quan-ly/tin-dang/" + targetId, null);
                } else {
                    // Không thể khóa bình luận/đánh giá/tài khoản trực tiếp: ẩn nội dung (nếu có) và
                    // đề xuất khóa tài khoản cho Admin (không tự khóa — canonical §10).
                    actionType = ModerationActionType.HIDE;
                    targetAction = hideTarget(targetType, targetId, listingId, moderatorId, actionReason);
                }
            }
            default -> actionType = ModerationActionType.DISMISS;
        }

        ModerationAction action = recordAction(actionType, targetType, targetId, listingId, reportId,
                moderatorId, false, result, actionReason, internalNote,
                targetAction == null ? null : targetAction.getPreviousStatus(),
                targetAction == null ? null : targetAction.getNewStatus());

        boolean warningIssued = false;
        Long warningId = null;
        if (result != ModerationResult.NO_VIOLATION && ownerId != null && warningMessage != null) {
            ViolationWarning w = issueWarning(ownerId, listingId, reportId, action.getId(), severity,
                    warningMessage, moderatorId, false);
            warningIssued = true;
            warningId = w.getId();
            notificationService.notifyUser(ownerId, NotificationType.VIOLATION_WARNING,
                    "Bạn nhận được một cảnh báo vi phạm", warningMessage, "/tai-khoan/canh-bao", null);
        }
        return new TargetOutcome(targetAction, action.getId(), warningIssued, warningId);
    }

    private ResolveReportResponse.TargetAction hideTarget(ReportTargetType targetType, Long targetId,
                                                          Long listingId, Long moderatorId, String reason) {
        switch (targetType) {
            case LISTING -> {
                ListingStatus prev = listingGateway.hideByModeration(targetId, moderatorId, reason);
                return ResolveReportResponse.TargetAction.builder()
                        .type("LISTING_HIDDEN").targetId(targetId)
                        .previousStatus(prev == null ? null : prev.name())
                        .newStatus(ListingStatus.HIDDEN.name()).build();
            }
            case COMMENT -> {
                contentGateway.hideComment(targetId, moderatorId, reason);
                return ResolveReportResponse.TargetAction.builder()
                        .type("COMMENT_HIDDEN").targetId(targetId).newStatus("HIDDEN").build();
            }
            case REVIEW -> {
                contentGateway.hideReview(targetId, moderatorId, reason);
                return ResolveReportResponse.TargetAction.builder()
                        .type("REVIEW_HIDDEN").targetId(targetId).newStatus("HIDDEN").build();
            }
            default -> {
                // USER: không có nội dung để ẩn; chỉ cảnh báo.
                return null;
            }
        }
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    private Specification<Report> base() {
        return ReportSpecifications.notDeleted();
    }

    private Report getAliveReport(Long id) {
        return reportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_NOT_FOUND));
    }

    private boolean isTerminal(ReportStatus status) {
        return status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED;
    }

    private void guardLockPermission(ModerationResult result) {
        if (result == ModerationResult.SEVERE_LOCK && !SecurityUtils.hasRole(RoleCode.ADMIN)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Khóa tin là quyền của Admin");
        }
    }

    /**
     * Ép bất biến "báo cáo kết thúc luôn có resolved_by/resolved_at/is_valid" (canonical 4.16.4:
     * ràng buộc DB không CHECK được).
     */
    private void closeReport(Report report, ModerationResult result, Long moderatorId, String publicResponse) {
        report.setStatus(result == ModerationResult.NO_VIOLATION ? ReportStatus.REJECTED : ReportStatus.RESOLVED);
        report.setIsValid(result != ModerationResult.NO_VIOLATION);
        report.setResolvedBy(moderatorId);
        report.setResolvedAt(Instant.now());
        report.setResolutionNote(publicResponse);
        reportRepository.save(report);
    }

    private void recalcTrust(TargetInfo target) {
        try {
            if (target.listingId() != null) {
                trustScoreService.recalculateAndSaveListing(target.listingId());
            }
            if (target.ownerId() != null) {
                trustScoreService.recalculateLandlord(target.ownerId());
            }
        } catch (RuntimeException e) {
            log.warn("Không tính lại được điểm uy tín (bỏ qua để không chặn kiểm duyệt): {}", e.getMessage());
        }
    }

    private boolean maybeSuspendPosting(Long ownerId, boolean warningIssued, Long ownerWarn30) {
        if (ownerId == null || !warningIssued || ownerWarn30 == null) {
            return false;
        }
        int threshold = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_WARNING_COUNT);
        if (ownerWarn30 >= threshold) {
            int days = systemConfig.getInt(ConfigKey.MOD_THRESHOLD_WARNING_WINDOW_DAYS);
            userGateway.suspendPosting(ownerId, Instant.now().plus(Duration.ofDays(days)),
                    "Vượt ngưỡng " + threshold + " cảnh báo trong " + days + " ngày");
            return true;
        }
        return false;
    }

    private Long writeLockAuditIfNeeded(ModerationResult result, ReportTargetType targetType, Long targetId,
                                        String targetLabel, Long moderatorId, String reason) {
        if (result == ModerationResult.SEVERE_LOCK && targetType == ReportTargetType.LISTING) {
            return auditGateway.record(AuditAction.LISTING_LOCK, moderatorId, "LISTING", targetId,
                    targetLabel, reason);
        }
        return null;
    }

    private ModerationAction recordAction(ModerationActionType type, ReportTargetType targetType, Long targetId,
                                          Long listingId, Long reportId, Long moderatorId, boolean system,
                                          ModerationResult result, String reason, String note,
                                          String previousStatus, String newStatus) {
        ModerationAction action = ModerationAction.builder()
                .moderatorId(moderatorId)
                .isSystem(system)
                .reportId(reportId)
                .targetType(targetType)
                .targetId(targetId)
                .listingId(listingId)
                .actionType(type)
                .result(result)
                .reason(reason == null || reason.isBlank() ? type.getLabel() : reason)
                .note(note)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .build();
        return moderationActionRepository.save(action);
    }

    private ViolationWarning issueWarning(Long userId, Long listingId, Long reportId, Long actionId,
                                          ReportSeverity severity, String message, Long issuerId, boolean system) {
        ViolationWarning warning = ViolationWarning.builder()
                .userId(userId)
                .listingId(listingId)
                .reportId(reportId)
                .moderationActionId(actionId)
                .severity(severity)
                .reason(truncate(message, 200))
                .content(truncate(message, 1000))
                .issuedBy(system ? null : issuerId)
                .isSystem(system)
                .build();
        return violationWarningRepository.save(warning);
    }

    private ModerationResult latestResultOf(Long reportId) {
        ModerationAction action = latestActionOf(reportId);
        return action == null ? null : action.getResult();
    }

    private ModerationAction latestActionOf(Long reportId) {
        if (reportId == null) {
            return null;
        }
        List<ModerationAction> actions = moderationActionRepository.findByReportIdOrderByCreatedAtDesc(reportId);
        return actions.isEmpty() ? null : actions.get(0);
    }

    private ReportSeverity severityOf(ReportReason reason) {
        return switch (reason) {
            case SCAM -> ReportSeverity.CRITICAL;
            case FAKE_IMAGE, OFFENSIVE -> ReportSeverity.HIGH;
            case WRONG_INFO, WRONG_PRICE, SPAM -> ReportSeverity.MEDIUM;
            case ALREADY_RENTED, OTHER -> ReportSeverity.LOW;
        };
    }

    private ReporterInfo buildReporterInfo(Long reporterId, boolean withDetails) {
        long total = reportRepository.count(base().and(ReportSpecifications.reporter(reporterId)));
        long valid = reportRepository.count(base().and(ReportSpecifications.reporter(reporterId))
                .and(ReportSpecifications.isValidTrue()));
        long rejected = reportRepository.count(base().and(ReportSpecifications.reporter(reporterId))
                .and(ReportSpecifications.statusEq(ReportStatus.REJECTED)));
        ReporterInfo.ReporterInfoBuilder builder = ReporterInfo.builder()
                .id(reporterId)
                .reportCount(total)
                .validReportCount(valid)
                .rejectedReportCount(rejected);
        userGateway.findRef(reporterId).ifPresent(u -> {
            builder.fullName(u.fullName());
            if (withDetails) {
                builder.email(u.email());
                if (u.memberSince() != null) {
                    builder.accountAgeDays(ChronoUnit.DAYS.between(u.memberSince(), Instant.now()));
                }
            }
        });
        return builder.build();
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return HtmlSanitizer.stripAllHtml(value);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    // -------- Đối tượng bị báo cáo --------

    /** Thông tin tối thiểu cần cho nghiệp vụ (chủ sở hữu, tin liên quan, tiêu đề). */
    private record TargetInfo(Long ownerId, Long listingId, String title, String status) {
    }

    /** Thông tin đầy đủ để hiển thị (thêm chủ sở hữu, điểm uy tín, trích nội dung). */
    private record TargetView(String title, String status, String excerpt, String url,
                              Long ownerId, String ownerName, Integer ownerTrustScore) {
    }

    private TargetInfo resolveTarget(ReportTargetType type, Long targetId) {
        return switch (type) {
            case LISTING -> {
                ListingModerationGateway.ListingRef ref = listingGateway.findRef(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                yield new TargetInfo(ref.ownerId(), ref.id(), ref.title(),
                        ref.status() == null ? null : ref.status().name());
            }
            case COMMENT -> {
                ContentModerationGateway.ContentRef c = contentGateway.findComment(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                yield new TargetInfo(c.authorId(), c.listingId(), c.excerpt(), null);
            }
            case REVIEW -> {
                ContentModerationGateway.ContentRef r = contentGateway.findReview(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                yield new TargetInfo(r.authorId(), r.listingId(), r.excerpt(), null);
            }
            case USER -> {
                UserModerationGateway.UserRef u = userGateway.findRef(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                yield new TargetInfo(u.id(), null, u.fullName(), null);
            }
        };
    }

    private TargetInfo resolveTargetOrThrow(ReportTargetType type, Long targetId) {
        return resolveTarget(type, targetId);
    }

    private String view(TargetInfo t) {
        return t.title();
    }

    /** Mô tả đối tượng để hiển thị; không ném khi thiếu (trả tiêu đề rỗng) — dùng cho list. */
    private TargetView describeTarget(ReportTargetType type, Long targetId) {
        try {
            return describeTargetOrThrow(type, targetId);
        } catch (RuntimeException e) {
            return new TargetView(null, null, null, null, null, null, null);
        }
    }

    private TargetView describeTargetOrThrow(ReportTargetType type, Long targetId) {
        switch (type) {
            case LISTING -> {
                ListingModerationGateway.ListingRef ref = listingGateway.findRef(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                UserModerationGateway.UserRef owner = userGateway.findRef(ref.ownerId()).orElse(null);
                String url = ref.slug() == null ? null : "/tin/" + ref.slug() + "-" + ref.id();
                return new TargetView(ref.title(), ref.status() == null ? null : ref.status().name(),
                        null, url, ref.ownerId(),
                        owner == null ? null : owner.fullName(),
                        owner == null ? null : owner.trustScore());
            }
            case COMMENT -> {
                ContentModerationGateway.ContentRef c = contentGateway.findComment(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                UserModerationGateway.UserRef author = c.authorId() == null ? null
                        : userGateway.findRef(c.authorId()).orElse(null);
                return new TargetView("Bình luận #" + targetId, null, c.excerpt(), null, c.authorId(),
                        author == null ? null : author.fullName(),
                        author == null ? null : author.trustScore());
            }
            case REVIEW -> {
                ContentModerationGateway.ContentRef r = contentGateway.findReview(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                UserModerationGateway.UserRef author = r.authorId() == null ? null
                        : userGateway.findRef(r.authorId()).orElse(null);
                return new TargetView("Đánh giá #" + targetId, null, r.excerpt(), null, r.authorId(),
                        author == null ? null : author.fullName(),
                        author == null ? null : author.trustScore());
            }
            default -> {
                UserModerationGateway.UserRef u = userGateway.findRef(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                return new TargetView(u.fullName(), null, null, "/nguoi-dung/" + targetId, u.id(),
                        u.fullName(), u.trustScore());
            }
        }
    }
}
