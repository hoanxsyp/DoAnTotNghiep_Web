package com.webtro.modules.admin.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.UserStatus;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.common.enums.VerificationType;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.dto.request.LockUserRequest;
import com.webtro.modules.admin.dto.request.RejectLandlordVerificationRequest;
import com.webtro.modules.admin.dto.request.RestrictLandlordPostingRequest;
import com.webtro.modules.admin.dto.request.UnlockUserRequest;
import com.webtro.modules.admin.dto.request.UnverifyLandlordRequest;
import com.webtro.modules.admin.dto.request.UpdateRoleRequest;
import com.webtro.modules.admin.dto.request.VerifyLandlordRequest;
import com.webtro.modules.admin.dto.response.AdminLandlordResponse;
import com.webtro.modules.admin.dto.response.AdminUserDetailResponse;
import com.webtro.modules.admin.dto.response.AdminUserResponse;
import com.webtro.modules.admin.dto.response.LandlordPostingRestrictionResponse;
import com.webtro.modules.admin.dto.response.LandlordVerificationActionResponse;
import com.webtro.modules.admin.dto.response.UserActionResponse;
import com.webtro.modules.admin.mapper.AdminUserMapper;
import com.webtro.modules.admin.service.AdminUserService;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.specification.AdminLandlordSpecifications;
import com.webtro.modules.admin.specification.AdminUserSpecifications;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingCategoryCountPublisher;
import com.webtro.modules.listing.service.ListingOwnerStatsPublisher;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.RefreshToken;
import com.webtro.modules.user.entity.Role;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserProfile;
import com.webtro.modules.user.entity.Verification;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.RefreshTokenRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserProfileRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.repository.VerificationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cài đặt {@link AdminUserService}. Kiểm quyền sở hữu/ranh giới ("không tự khóa mình", "không sửa
 * Admin khác") ở đây; ghi audit qua {@link AuditLogService}; thông báo qua {@link NotificationService}.
 *
 * <p>Thao tác khóa/mở khóa <b>thu hồi toàn bộ refresh token</b> để quyền/khóa có hiệu lực ngay
 * (canonical §8). Khóa/mở tin đi qua {@link ListingStateMachine} — không set trạng thái trực tiếp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** Trạng thái tin có thể bị khóa cùng khi khóa tài khoản (canonical §5.1). */
    private static final Set<ListingStatus> LOCKABLE = Set.of(
            ListingStatus.ACTIVE, ListingStatus.NEED_REVIEW, ListingStatus.HIDDEN, ListingStatus.PENDING);

    /** Các trường sort hợp lệ cho màn quản lý chủ trọ (canonical 4.13.6) → cột entity tương ứng. */
    private static final Map<String, String> LANDLORD_SORT_FIELDS = Map.of(
            "trustScore", "trustScore",
            "listingCount", "totalListings",
            "createdAt", "createdAt",
            "validReportCount", "validReportCount");

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final VerificationRepository verificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ListingRepository listingRepository;
    private final ListingStateMachine listingStateMachine;
    private final ListingCategoryCountPublisher categoryCountPublisher;
    private final ListingOwnerStatsPublisher ownerStatsPublisher;
    private final TrustScoreService trustScoreService;
    private final AdminUserMapper adminUserMapper;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    // ============================ Tìm/lọc ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> searchUsers(String keyword, List<String> roles,
                                                       List<UserStatus> statuses, Boolean verified,
                                                       Instant from, Instant to, Pageable pageable) {
        Page<User> page = userRepository.findAll(
                AdminUserSpecifications.filter(keyword, roles, statuses, verified, from, to), pageable);
        return PageResponse.from(page, this::toResponse);
    }

    private AdminUserResponse toResponse(User user) {
        LandlordProfile profile = landlordProfileRepository
                .findByUser_IdAndDeletedAtIsNull(user.getId()).orElse(null);
        return adminUserMapper.toResponse(user, roleCodeOf(user), profile);
    }

    /** Mã vai trò duy nhất của người dùng — đọc thẳng từ quan hệ, không cần truy vấn phụ. */
    private String roleCodeOf(User user) {
        return user.getRole() == null ? null : user.getRole().getCode();
    }

    // ============================ Khóa ============================

    @Override
    @Transactional
    public UserActionResponse lockUser(Long userId, LockUserRequest request, Long actorId) {
        String reason = request.getReason();
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.LOCK_REASON_REQUIRED,
                    "Vui lòng nhập lý do khóa tài khoản");
        }
        if (userId.equals(actorId)) {
            throw new BusinessException(ErrorCode.CANNOT_LOCK_SELF,
                    "Bạn không thể tự khóa tài khoản của mình");
        }

        User user = getAliveUser(userId);
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ConflictException(ErrorCode.USER_ALREADY_LOCKED);
        }
        assertNotOtherAdmin(userId);

        UserStatus previous = user.getStatus();
        user.setStatus(UserStatus.LOCKED);
        user.setLockReason(reason);
        user.setLockedBy(actorId);
        user.setLockedAt(Instant.now());
        userRepository.save(user);

        int revoked = revokeSessions(userId, "ADMIN_LOCK");

        int lockedListings = 0;
        if (Boolean.TRUE.equals(request.getLockListings())) {
            lockedListings = lockOwnerListings(userId, reason);
        }

        Long auditId = auditLogService.recordChange(AuditAction.USER_LOCK, actorId, "USER", userId,
                user.getFullName(), previous != null ? previous.name() : null,
                UserStatus.LOCKED.name(), reason);

        boolean notified = false;
        if (!Boolean.FALSE.equals(request.getNotifyUser())) {
            notificationService.notifyUser(userId, NotificationType.ACCOUNT_LOCKED,
                    "Tài khoản của bạn đã bị khóa", "Lý do: " + reason);
            notified = true;
        }

        return UserActionResponse.builder()
                .userId(userId)
                .status(UserStatus.LOCKED.name())
                .previousStatus(previous != null ? previous.name() : null)
                .reason(reason)
                .lockedListingCount(lockedListings)
                .revokedSessionCount(revoked)
                .userNotified(notified)
                .auditLogId(auditId)
                .at(Instant.now())
                .build();
    }

    // ============================ Mở khóa ============================

    @Override
    @Transactional
    public UserActionResponse unlockUser(Long userId, UnlockUserRequest request, Long actorId) {
        User user = getAliveUser(userId);
        if (user.getStatus() != UserStatus.LOCKED) {
            throw new ConflictException(ErrorCode.USER_ALREADY_ACTIVE);
        }
        assertNotOtherAdmin(userId);

        UserStatus previous = user.getStatus();
        UserStatus target = user.getEmailVerifiedAt() != null
                ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFY;
        user.setStatus(target);
        user.setLockReason(null);
        user.setLockedBy(null);
        user.setLockedAt(null);
        userRepository.save(user);

        int unlockedListings = 0;
        if (Boolean.TRUE.equals(request.getUnlockListings())) {
            unlockedListings = unlockOwnerListings(userId, request.getReason());
        }

        Long auditId = auditLogService.recordChange(AuditAction.USER_UNLOCK, actorId, "USER", userId,
                user.getFullName(), previous != null ? previous.name() : null,
                target.name(), request.getReason());

        return UserActionResponse.builder()
                .userId(userId)
                .status(target.name())
                .previousStatus(previous != null ? previous.name() : null)
                .reason(request.getReason())
                .unlockedListingCount(unlockedListings)
                .auditLogId(auditId)
                .at(Instant.now())
                .build();
    }

    // ============================ Đổi vai trò ============================

    @Override
    @Transactional
    public UserActionResponse updateRole(Long userId, UpdateRoleRequest request, Long actorId) {
        User user = getAliveUser(userId);

        String previousRole = roleCodeOf(user);
        boolean targetIsAdmin = RoleCode.ADMIN.equals(previousRole);

        // Chuẩn hóa và kiểm tra mã vai trò trước khi truy DB.
        String desired = request.getRole() == null ? "" : request.getRole().trim();
        if (!RoleCode.ALL.contains(desired)) {
            throw new BusinessException(ErrorCode.ROLE_ASSIGN_INVALID,
                    "Vai trò không hợp lệ: " + desired);
        }

        // Không tự gỡ ROLE_ADMIN của chính mình.
        if (userId.equals(actorId) && targetIsAdmin && !RoleCode.ADMIN.equals(desired)) {
            throw new BusinessException(ErrorCode.ROLE_ASSIGN_INVALID,
                    "Không thể tự gỡ vai trò quản trị viên của chính mình");
        }
        // Không đổi vai trò của Admin khác.
        if (!userId.equals(actorId) && targetIsAdmin) {
            throw new ForbiddenException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }

        Instant now = Instant.now();

        // Thay thế vai trò — mỗi người dùng chỉ giữ đúng một vai trò.
        Role role = roleRepository.findByCodeAndDeletedAtIsNull(desired)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND,
                        "Không tìm thấy vai trò: " + desired));
        user.setRole(role);
        userRepository.save(user);

        // Tự tạo hồ sơ chủ trọ nếu vừa được cấp ROLE_LANDLORD.
        boolean profileCreated = false;
        if (RoleCode.LANDLORD.equals(desired)
                && !landlordProfileRepository.existsByUser_IdAndDeletedAtIsNull(userId)) {
            landlordProfileRepository.save(LandlordProfile.builder()
                    .user(user)
                    .contactName(user.getFullName())
                    .contactPhone(user.getPhone() != null ? user.getPhone() : "")
                    .contactEmail(user.getEmail())
                    .verificationStatus(VerificationStatus.PENDING)
                    .build());
            profileCreated = true;
        }

        int revoked = revokeSessions(userId, "ROLE_CHANGE");

        Long auditId = auditLogService.recordChange(AuditAction.ROLE_CHANGE, actorId, "USER", userId,
                user.getFullName(), previousRole, desired, request.getReason());

        return UserActionResponse.builder()
                .userId(userId)
                .previousRole(previousRole)
                .role(desired)
                .landlordProfileCreated(profileCreated)
                .revokedSessionCount(revoked)
                .auditLogId(auditId)
                .at(now)
                .build();
    }

    // ============================ Chi tiết người dùng ============================

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = getAliveUser(userId);
        UserProfile profile = userProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        LandlordProfile landlord = landlordProfileRepository
                .findByUser_IdAndDeletedAtIsNull(userId).orElse(null);

        AdminUserDetailResponse.AdminUserDetailResponseBuilder b = AdminUserDetailResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .role(roleCodeOf(user))
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .statusLabel(user.getStatus() != null ? user.getStatus().getLabel() : null)
                .emailVerified(user.getEmailVerifiedAt() != null)
                .phoneVerified(user.getPhoneVerifiedAt() != null)
                .lockReason(user.getLockReason())
                .lockedAt(user.getLockedAt())
                .lockedBy(user.getLockedBy())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt());

        if (profile != null) {
            b.dateOfBirth(profile.getDateOfBirth())
                    .bio(profile.getBio())
                    .occupation(profile.getOccupation())
                    .address(profile.getAddressDetail());
        }

        if (landlord != null) {
            boolean suspended = isPostingSuspended(landlord);
            b.landlordProfile(AdminUserDetailResponse.LandlordDetail.builder()
                    .businessName(landlord.getCompanyName())
                    .displayName(landlord.getDisplayName())
                    .contactName(landlord.getContactName())
                    .contactPhone(landlord.getContactPhone())
                    .contactEmail(landlord.getContactEmail())
                    .verificationStatus(landlord.getVerificationStatus() != null
                            ? landlord.getVerificationStatus().name() : null)
                    .verificationStatusLabel(landlord.getVerificationStatus() != null
                            ? landlord.getVerificationStatus().getLabel() : null)
                    .verifiedAt(landlord.getVerifiedAt())
                    .verifiedById(landlord.getVerifiedBy())
                    .verificationNote(landlord.getVerificationNote())
                    .trustScore(toInt(landlord.getTrustScore()))
                    .averageRating(landlord.getAverageRating())
                    .reviewCount(landlord.getReviewCount())
                    .responseRatePercent(landlord.getResponseRatePercent())
                    .totalListings(landlord.getTotalListings())
                    .activeListings(landlord.getTotalActiveListings())
                    .lockedListingCount(landlord.getLockedListingCount())
                    .validReportCount(landlord.getValidReportCount())
                    .warningCount(landlord.getWarningCount())
                    .postingSuspended(suspended)
                    .postingSuspendedUntil(suspended ? landlord.getPostingRestrictedUntil() : null)
                    .restrictReason(suspended ? landlord.getRestrictReason() : null)
                    .build());
        }

        return b.build();
    }

    // ============================ Chủ trọ: danh sách ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminLandlordResponse> listLandlords(String keyword,
                                                             List<VerificationStatus> verificationStatuses,
                                                             Integer minTrustScore, Integer maxTrustScore,
                                                             Boolean postingSuspended, Pageable pageable) {
        Instant now = Instant.now();
        Pageable effective = sanitizeLandlordPageable(pageable);
        Specification<LandlordProfile> spec = AdminLandlordSpecifications.filter(
                keyword, verificationStatuses, minTrustScore, maxTrustScore, postingSuspended, now);
        Page<LandlordProfile> page = landlordProfileRepository.findAll(spec, effective);

        // Nạp trước tên người xác thực để tránh N+1 (kích thước trang tối đa 100).
        Map<Long, String> verifierNames = loadVerifierNames(page.getContent());

        return PageResponse.from(page, lp -> toLandlordResponse(lp, verifierNames, now));
    }

    private Map<Long, String> loadVerifierNames(List<LandlordProfile> profiles) {
        Set<Long> ids = new LinkedHashSet<>();
        for (LandlordProfile lp : profiles) {
            if (lp.getVerifiedBy() != null) {
                ids.add(lp.getVerifiedBy());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (User u : userRepository.findAllById(ids)) {
                names.put(u.getId(), u.getFullName());
            }
        }
        return names;
    }

    private AdminLandlordResponse toLandlordResponse(LandlordProfile lp, Map<Long, String> verifierNames,
                                                     Instant now) {
        User user = lp.getUser();
        Integer trustScore = toInt(lp.getTrustScore());
        boolean suspended = isPostingSuspended(lp, now);
        return AdminLandlordResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .businessName(lp.getCompanyName())
                .displayName(lp.getDisplayName())
                .verificationStatus(lp.getVerificationStatus() != null
                        ? lp.getVerificationStatus().name() : null)
                .verificationStatusLabel(lp.getVerificationStatus() != null
                        ? lp.getVerificationStatus().getLabel() : null)
                .verifiedAt(lp.getVerifiedAt())
                .verifiedById(lp.getVerifiedBy())
                .verifiedByName(lp.getVerifiedBy() != null ? verifierNames.get(lp.getVerifiedBy()) : null)
                .trustScore(trustScore)
                .trustLabel(trustScore != null ? trustScoreService.labelOf(trustScore) : null)
                .listingCount(lp.getTotalListings())
                .activeListingCount(lp.getTotalActiveListings())
                .lockedListingCount(lp.getLockedListingCount())
                .averageRating(lp.getAverageRating())
                .reviewCount(lp.getReviewCount())
                .validReportCount(lp.getValidReportCount())
                .warningCount(lp.getWarningCount())
                .postingSuspended(suspended)
                .postingSuspendedUntil(suspended ? lp.getPostingRestrictedUntil() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ============================ Chủ trọ: xác thực ============================

    @Override
    @Transactional
    public LandlordVerificationActionResponse verifyLandlord(Long userId, VerifyLandlordRequest request,
                                                             Long actorId) {
        User user = getAliveUser(userId);
        assertIsLandlord(userId);
        LandlordProfile lp = getAliveLandlordProfile(userId);

        if (lp.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new ConflictException(ErrorCode.LANDLORD_ALREADY_VERIFIED);
        }

        VerificationStatus previous = lp.getVerificationStatus();
        Instant now = Instant.now();
        String note = request != null ? request.getNote() : null;

        lp.setVerificationStatus(VerificationStatus.VERIFIED);
        lp.setVerifiedAt(now);
        lp.setVerifiedBy(actorId);
        lp.setVerificationNote(note);
        landlordProfileRepository.save(lp);

        // Đồng bộ các yêu cầu xác thực chủ trọ đang chờ (verifications type = LANDLORD) → VERIFIED.
        markLandlordVerifications(userId, VerificationStatus.VERIFIED, actorId, now);

        Long auditId = auditLogService.recordChange(AuditAction.LANDLORD_VERIFY, actorId, "USER", userId,
                user.getFullName(), previous != null ? previous.name() : null,
                VerificationStatus.VERIFIED.name(), note);

        notificationService.notifyUser(userId, NotificationType.ACCOUNT_VERIFICATION,
                "Tài khoản chủ trọ đã được xác thực",
                "Hồ sơ chủ trọ của bạn đã được xác thực. Huy hiệu \"Đã xác thực\" sẽ hiển thị trên tin đăng của bạn.");

        return LandlordVerificationActionResponse.builder()
                .userId(userId)
                .verificationStatus(VerificationStatus.VERIFIED.name())
                .previousStatus(previous != null ? previous.name() : null)
                .verifiedById(actorId)
                .verifiedByName(resolveUserName(actorId))
                .verifiedAt(now)
                .auditLogId(auditId)
                .userNotified(true)
                .updatedAt(now)
                .build();
    }

    @Override
    @Transactional
    public LandlordVerificationActionResponse unverifyLandlord(Long userId, UnverifyLandlordRequest request,
                                                               Long actorId) {
        User user = getAliveUser(userId);
        assertIsLandlord(userId);
        LandlordProfile lp = getAliveLandlordProfile(userId);

        if (lp.getVerificationStatus() != VerificationStatus.VERIFIED) {
            // Chưa VERIFIED thì không có gì để hủy (canonical 4.13.8).
            throw new ConflictException(ErrorCode.LANDLORD_NOT_VERIFIED);
        }

        String reason = request.getReason();
        VerificationStatus previous = lp.getVerificationStatus();
        Instant now = Instant.now();

        // Về hàng đợi chờ duyệt; KHÔNG đụng posting_restricted_until (canonical 4.13.8 quy tắc 2).
        lp.setVerificationStatus(VerificationStatus.PENDING);
        lp.setVerifiedAt(null);
        lp.setVerifiedBy(null);
        lp.setVerificationNote(reason);
        landlordProfileRepository.save(lp);

        markLandlordVerifications(userId, VerificationStatus.PENDING, actorId, null);

        Long auditId = auditLogService.recordChange(AuditAction.LANDLORD_UNVERIFY, actorId, "USER", userId,
                user.getFullName(), previous.name(), VerificationStatus.PENDING.name(), reason);

        notificationService.notifyUser(userId, NotificationType.ACCOUNT_VERIFICATION,
                "Đã hủy xác thực tài khoản chủ trọ",
                "Hồ sơ chủ trọ của bạn đã bị hủy xác thực và quay lại hàng đợi chờ duyệt. Lý do: " + reason);

        return LandlordVerificationActionResponse.builder()
                .userId(userId)
                .verificationStatus(VerificationStatus.PENDING.name())
                .previousStatus(previous.name())
                .reason(reason)
                .verifiedAt(null)
                .auditLogId(auditId)
                .userNotified(true)
                .updatedAt(now)
                .build();
    }

    // ============================ Chủ trọ: từ chối xác thực ============================

    @Override
    @Transactional
    public LandlordVerificationActionResponse rejectLandlordVerification(
            Long userId, RejectLandlordVerificationRequest request, Long actorId) {
        User user = getAliveUser(userId);
        assertIsLandlord(userId);
        LandlordProfile lp = getAliveLandlordProfile(userId);

        if (lp.getVerificationStatus() == VerificationStatus.VERIFIED) {
            // Đã xác thực rồi thì phải dùng "hủy xác thực", không phải "từ chối" (canonical 4.13.7/4.13.8).
            throw new ConflictException(ErrorCode.LANDLORD_ALREADY_VERIFIED);
        }
        if (lp.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Hồ sơ chủ trọ đã ở trạng thái bị từ chối");
        }

        String reason = request.getReason();
        VerificationStatus previous = lp.getVerificationStatus();
        Instant now = Instant.now();

        lp.setVerificationStatus(VerificationStatus.REJECTED);
        lp.setVerifiedAt(null);
        lp.setVerifiedBy(null);
        lp.setVerificationNote(reason);
        landlordProfileRepository.save(lp);

        markLandlordVerifications(userId, VerificationStatus.REJECTED, actorId, null);

        Long auditId = auditLogService.recordChange(AuditAction.LANDLORD_UNVERIFY, actorId, "USER", userId,
                user.getFullName(), previous != null ? previous.name() : null,
                VerificationStatus.REJECTED.name(), reason);

        notificationService.notifyUser(userId, NotificationType.ACCOUNT_VERIFICATION,
                "Yêu cầu xác thực chủ trọ bị từ chối",
                "Yêu cầu xác thực chủ trọ của bạn đã bị từ chối. Lý do: " + reason);

        return LandlordVerificationActionResponse.builder()
                .userId(userId)
                .verificationStatus(VerificationStatus.REJECTED.name())
                .previousStatus(previous != null ? previous.name() : null)
                .reason(reason)
                .verifiedAt(null)
                .auditLogId(auditId)
                .userNotified(true)
                .updatedAt(now)
                .build();
    }

    // ============================ Chủ trọ: hạn chế đăng tin ============================

    @Override
    @Transactional
    public LandlordPostingRestrictionResponse restrictLandlordPosting(
            Long userId, RestrictLandlordPostingRequest request, Long actorId) {
        User user = getAliveUser(userId);
        assertIsLandlord(userId);
        LandlordProfile lp = getAliveLandlordProfile(userId);

        Instant until = request.getRestrictedUntil();
        String reason = request.getReason();
        Instant now = Instant.now();
        if (until == null || !until.isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Thời điểm hết hạn chế phải ở tương lai");
        }

        Instant previousUntil = lp.getPostingRestrictedUntil();
        lp.setPostingRestrictedUntil(until);
        lp.setRestrictReason(reason);
        landlordProfileRepository.save(lp);

        Long auditId = auditLogService.recordChange(AuditAction.USER_LOCK, actorId, "USER", userId,
                user.getFullName(),
                previousUntil != null ? "POSTING_RESTRICTED_UNTIL " + previousUntil : "POSTING_ALLOWED",
                "POSTING_RESTRICTED_UNTIL " + until, reason);

        notificationService.notifyUser(userId, NotificationType.VIOLATION_WARNING,
                "Chức năng đăng tin của bạn bị tạm hạn chế",
                "Bạn sẽ không thể đăng tin mới đến " + until + ". Lý do: " + reason);

        return LandlordPostingRestrictionResponse.builder()
                .userId(userId)
                .postingSuspended(true)
                .postingRestrictedUntil(until)
                .reason(reason)
                .auditLogId(auditId)
                .userNotified(true)
                .updatedAt(now)
                .build();
    }

    // ============================ Nội bộ ============================

    /** Kiểm tra người dùng có vai trò chủ trọ; ném {@code TARGET_NOT_LANDLORD} nếu không. */
    private void assertIsLandlord(Long userId) {
        if (!RoleCode.LANDLORD.equals(roleRepository.findRoleCodeByUserId(userId).orElse(null))) {
            throw new BusinessException(ErrorCode.TARGET_NOT_LANDLORD);
        }
    }

    private LandlordProfile getAliveLandlordProfile(Long userId) {
        return landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANDLORD_PROFILE_NOT_FOUND));
    }

    /** Cập nhật trạng thái các yêu cầu xác thực chủ trọ đang chờ của người dùng. */
    private void markLandlordVerifications(Long userId, VerificationStatus status, Long actorId,
                                           Instant verifiedAt) {
        List<Verification> pending = verificationRepository
                .findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(
                        userId, VerificationType.LANDLORD, VerificationStatus.PENDING);
        Instant now = Instant.now();
        for (Verification v : pending) {
            v.setStatus(status);
            v.setReviewedBy(actorId);
            v.setReviewedAt(now);
            if (status == VerificationStatus.VERIFIED) {
                v.setVerifiedAt(verifiedAt != null ? verifiedAt : now);
            }
            verificationRepository.save(v);
        }
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId).map(User::getFullName).orElse(null);
    }

    private boolean isPostingSuspended(LandlordProfile lp) {
        return isPostingSuspended(lp, Instant.now());
    }

    private boolean isPostingSuspended(LandlordProfile lp, Instant now) {
        Instant until = lp.getPostingRestrictedUntil();
        return until != null && until.isAfter(now);
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** Giữ page/size (size ≤ 100); ánh xạ + whitelist trường sort (canonical 4.13.6). */
    private Pageable sanitizeLandlordPageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = pageable.getPageSize();
        if (size <= 0) {
            size = 20;
        }
        size = Math.min(size, MAX_PAGE_SIZE);

        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            String mapped = LANDLORD_SORT_FIELDS.get(o.getProperty());
            if (mapped == null) {
                throw new BusinessException(ErrorCode.INVALID_SORT_FIELD,
                        "Không thể sắp xếp theo trường " + o.getProperty());
            }
            orders.add(new Sort.Order(o.getDirection(), mapped));
        }
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Direction.ASC, "trustScore") : Sort.by(orders);
        return PageRequest.of(page, size, sort);
    }

    private User getAliveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    /** Chặn thao tác lên Admin khác (self đã bị chặn riêng ở nơi gọi khi cần). */
    private void assertNotOtherAdmin(Long userId) {
        if (RoleCode.ADMIN.equals(roleRepository.findRoleCodeByUserId(userId).orElse(null))) {
            throw new ForbiddenException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
    }

    private int revokeSessions(Long userId, String reason) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUser_IdAndDeletedAtIsNull(userId);
        int count = 0;
        Instant now = Instant.now();
        for (RefreshToken t : tokens) {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(now);
                t.setRevokedReason(reason);
                refreshTokenRepository.save(t);
                count++;
            }
        }
        return count;
    }

    private int lockOwnerListings(Long ownerId, String reason) {
        int count = 0;
        for (Listing l : listingRepository.findAll(ownerStatusSpec(ownerId, LOCKABLE))) {
            ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(l);
            ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(l);
            ListingStatus newStatus = listingStateMachine.transition(l.getStatus(), ListingEvent.LOCK);
            l.setStatus(newStatus);
            l.setLockReason(reason);
            listingRepository.save(l);
            categoryCountPublisher.publishIfChanged(categoryCountBefore, l);
            ownerStatsPublisher.publishIfChanged(ownerStatsBefore, l);
            count++;
        }
        return count;
    }

    private int unlockOwnerListings(Long ownerId, String reason) {
        int count = 0;
        for (Listing l : listingRepository.findAll(ownerStatusSpec(ownerId, Set.of(ListingStatus.LOCKED)))) {
            ListingStatus newStatus = listingStateMachine.transition(l.getStatus(), ListingEvent.UNLOCK);
            l.setStatus(newStatus);
            listingRepository.save(l);
            count++;
        }
        return count;
    }

    private Specification<Listing> ownerStatusSpec(Long ownerId, Set<ListingStatus> statuses) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("ownerId"), ownerId));
            ps.add(root.get("status").in(statuses));
            ps.add(cb.isNull(root.get("deletedAt")));
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
