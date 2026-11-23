package com.webtro.modules.moderation.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.RateLimitException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.moderation.dto.request.CreateReportRequest;
import com.webtro.modules.moderation.entity.Report;
import com.webtro.modules.moderation.mapper.ModerationActionMapper;
import com.webtro.modules.moderation.mapper.ReportMapper;
import com.webtro.modules.moderation.mapper.WarningMapper;
import com.webtro.modules.moderation.repository.ModerationActionRepository;
import com.webtro.modules.moderation.repository.ReportRepository;
import com.webtro.modules.moderation.repository.ViolationWarningRepository;
import com.webtro.modules.moderation.spi.AuditGateway;
import com.webtro.modules.moderation.spi.ContentModerationGateway;
import com.webtro.modules.moderation.spi.ListingModerationGateway;
import com.webtro.modules.moderation.spi.UserModerationGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.RateLimitService;
import com.webtro.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT người thuê gửi báo cáo vi phạm (UT-RPT-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationServiceReportTenantTest {

    private static final long TENANT_ID = 30L;
    private static final long OWNER_ID = 40L;
    private static final long LISTING_ID = 20L;

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ModerationActionRepository moderationActionRepository;
    @Mock
    private ViolationWarningRepository violationWarningRepository;
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private WarningMapper warningMapper;
    @Mock
    private ModerationActionMapper moderationActionMapper;
    @Mock
    private SystemConfigService systemConfig;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ListingModerationGateway listingGateway;
    @Mock
    private UserModerationGateway userGateway;
    @Mock
    private ContentModerationGateway contentGateway;
    @Mock
    private AuditGateway auditGateway;
    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private ModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(systemConfig.getInt(ConfigKey.SPAM_REPORT_DAILY)).thenReturn(10);
        when(systemConfig.getInt(ConfigKey.REPORT_ABUSE_WINDOW_DAYS)).thenReturn(30);
        when(systemConfig.getInt(ConfigKey.REPORT_ABUSE_REJECTED_COUNT)).thenReturn(5);
        when(systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_REPORT_COUNT)).thenReturn(5);
        when(systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_DISTINCT_REPORTERS)).thenReturn(5);
        when(systemConfig.getInt(ConfigKey.MOD_AUTOHIDE_WINDOW_HOURS)).thenReturn(24);
        when(systemConfig.getInt(ConfigKey.LISTING_IMAGE_MAX_SIZE_MB)).thenReturn(5);
        when(listingGateway.findRef(LISTING_ID)).thenReturn(Optional.of(new ListingModerationGateway.ListingRef(
                LISTING_ID, OWNER_ID, "Phòng test", "phong-test", ListingStatus.ACTIVE,
                BigDecimal.ONE, BigDecimal.ONE, "Hà Nội", null)));
        when(reportRepository.count(any(Specification.class))).thenReturn(0L);
        when(reportRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("UT-RPT-01 Báo cáo tin hợp lệ → PENDING, severity theo lý do (SCAM=CRITICAL), lọc HTML mô tả")
    void createListingReport() {
        service.createReport(req(ReportReason.SCAM, "<b>Đòi cọc trước</b> rồi chặn liên lạc"), TENANT_ID);

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(saved.capture());
        Report r = saved.getValue();
        assertEquals(ReportStatus.PENDING, r.getStatus());
        assertEquals(ReportSeverity.CRITICAL, r.getSeverity());
        assertEquals(LISTING_ID, r.getListingId());
        assertEquals("Đòi cọc trước rồi chặn liên lạc", r.getDescription());
        assertEquals("LISTING:" + LISTING_ID + ":SCAM", r.getDedupKey());
    }

    @Test
    @DisplayName("UT-RPT-02 Lý do OTHER mà không mô tả → REPORT_DESCRIPTION_REQUIRED")
    void otherRequiresDescription() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReport(req(ReportReason.OTHER, "  "), TENANT_ID));

        assertEquals(ErrorCode.REPORT_DESCRIPTION_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RPT-03 Tự báo cáo nội dung của mình → REPORT_SELF_FORBIDDEN")
    void cannotReportOwnContent() {
        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createReport(req(ReportReason.SPAM, null), OWNER_ID));

        assertEquals(ErrorCode.REPORT_SELF_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RPT-04 Báo cáo trùng (cùng đối tượng + lý do, bản cũ còn PENDING) → REPORT_DUPLICATE")
    void duplicateOpenReport() {
        Report open = Report.builder().reporterId(TENANT_ID).status(ReportStatus.PENDING).build();
        when(reportRepository.findAll(any(Specification.class))).thenReturn(List.of(open));

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.createReport(req(ReportReason.SPAM, null), TENANT_ID));

        assertEquals(ErrorCode.REPORT_DUPLICATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RPT-05 Báo cáo lại sau khi bản cũ đã xử lý → cho phép, dedupKey thêm hậu tố chu kỳ #1")
    void reReportAfterResolved() {
        Report resolved = Report.builder().reporterId(TENANT_ID).status(ReportStatus.RESOLVED).build();
        when(reportRepository.findAll(any(Specification.class))).thenReturn(List.of(resolved));

        service.createReport(req(ReportReason.SPAM, null), TENANT_ID);

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(saved.capture());
        assertEquals("LISTING:" + LISTING_ID + ":SPAM#1", saved.getValue().getDedupKey());
    }

    @Test
    @DisplayName("UT-RPT-06 Đối tượng không tồn tại → REPORT_TARGET_NOT_FOUND")
    void targetMissing() {
        when(listingGateway.findRef(LISTING_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.createReport(req(ReportReason.SPAM, null), TENANT_ID));

        assertEquals(ErrorCode.REPORT_TARGET_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RPT-07 Vượt số báo cáo/ngày → REPORT_RATE_LIMIT (429)")
    void rateLimited() {
        doThrow(new RateLimitException(ErrorCode.RATE_LIMIT_EXCEEDED, 100L))
                .when(rateLimitService).checkLimit(eq("report"), anyString(), anyInt(), any(Duration.class));

        BusinessException ex = assertThrows(RateLimitException.class,
                () -> service.createReport(req(ReportReason.SPAM, null), TENANT_ID));

        assertEquals(ErrorCode.REPORT_RATE_LIMIT, ex.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-RPT-08 Có ≥ ngưỡng báo cáo sai bị REJECTED trong cửa sổ → REPORT_RESTRICTED_ABUSE")
    void abusiveReporterBlocked() {
        when(reportRepository.count(any(Specification.class))).thenReturn(5L);

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.createReport(req(ReportReason.SPAM, null), TENANT_ID));

        assertEquals(ErrorCode.REPORT_RESTRICTED_ABUSE, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RPT-09 Báo cáo vượt ngưỡng (5 báo cáo / 5 người / 24h) → tự gắn cờ NEED_REVIEW + báo kiểm duyệt viên")
    void autoFlagWhenThresholdReached() {
        List<Report> recent = new java.util.ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            recent.add(Report.builder().reporterId(100 + i).status(ReportStatus.PENDING).build());
        }
        // Lần findAll đầu cho dedup (rỗng), lần sau cho đếm ngưỡng.
        when(reportRepository.findAll(any(Specification.class))).thenReturn(List.of()).thenReturn(recent);

        service.createReport(req(ReportReason.SCAM, null), TENANT_ID);

        verify(listingGateway).flagNeedReview(eq(LISTING_ID), anyString());
    }

    private CreateReportRequest req(ReportReason reason, String description) {
        return CreateReportRequest.builder()
                .targetType(ReportTargetType.LISTING)
                .targetId(LISTING_ID)
                .reason(reason)
                .description(description)
                .build();
    }
}
