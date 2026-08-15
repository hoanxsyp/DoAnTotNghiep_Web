package com.webtro.modules.user.service.impl;

import com.webtro.common.enums.TrustLabel;
import com.webtro.common.enums.UserStatus;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.common.enums.VerificationType;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.user.dto.request.LandlordVerificationRequest;
import com.webtro.modules.user.dto.request.UpdateContactRequest;
import com.webtro.modules.user.dto.request.UpdateLandlordProfileRequest;
import com.webtro.modules.user.dto.request.UpdateProfileRequest;
import com.webtro.modules.user.dto.response.AvatarResponse;
import com.webtro.modules.user.dto.response.ContactInfoUpdateResponse;
import com.webtro.modules.user.dto.response.LandlordPublicResponse;
import com.webtro.modules.user.dto.response.LandlordVerificationResponse;
import com.webtro.modules.user.dto.response.MyLandlordProfileResponse;
import com.webtro.modules.user.dto.response.UserProfileResponse;
import com.webtro.modules.user.dto.response.UserRefResponse;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserProfile;
import com.webtro.modules.user.entity.Verification;
import com.webtro.modules.user.mapper.LandlordProfileMapper;
import com.webtro.modules.user.mapper.UserMapper;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserProfileRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.repository.VerificationRepository;
import com.webtro.modules.user.service.UserService;
import com.webtro.util.HtmlSanitizer;
import com.webtro.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Hiện thực nghiệp vụ người dùng (USER-01..06).
 *
 * <p>Điểm uy tín/nhãn uy tín lấy qua {@link TrustScoreService} (module listing) — không tự tính
 * lại để giữ đúng công thức §5.8 ở một nơi duy nhất.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int MIN_AGE = 16;
    private static final String AVATAR_SUBDIR = "avatars";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final RoleRepository roleRepository;
    private final VerificationRepository verificationRepository;
    private final com.webtro.modules.user.repository.FollowRepository followRepository;

    private final UserMapper userMapper;
    private final LandlordProfileMapper landlordProfileMapper;

    private final SystemConfigService systemConfigService;
    private final com.webtro.storage.FileStorage fileStorage;

    /**
     * Tiêm {@code @Lazy} để phá vòng phụ thuộc tiềm ẩn user ↔ listing (nếu
     * {@code TrustScoreService} của module listing lại cần dữ liệu chủ trọ qua module user).
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private TrustScoreService trustScoreService;

    // ============================ Hồ sơ của tôi ============================

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = getById(userId);
        UserProfile profile = userProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        LandlordProfile landlord = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        String role = user.getRole() == null ? null : user.getRole().getCode();
        // landlordProfile chỉ nhúng khi user thực sự có vai trò chủ trọ.
        LandlordProfile embedded = RoleCode.LANDLORD.equals(role) ? landlord : null;
        return userMapper.toProfileResponse(user, profile, embedded, role);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);

        user.setFullName(request.getFullName().trim());
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        userRepository.save(user);

        validateAge(request.getDateOfBirth());
        UserProfile profile = userProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseGet(() -> UserProfile.builder().user(user).build());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setAddressDetail(sanitizeNullable(request.getAddress()));
        profile.setBio(sanitizeNullable(request.getBio()));
        userProfileRepository.save(profile);

        return getMyProfile(userId);
    }

    @Override
    @Transactional
    public ContactInfoUpdateResponse updateContact(Long userId, UpdateContactRequest request) {
        boolean hasName = request.getContactName() != null && !request.getContactName().isBlank();
        boolean hasPhone = request.getContactPhone() != null && !request.getContactPhone().isBlank();
        if (!hasName && !hasPhone) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Vui lòng nhập ít nhất tên liên hệ hoặc số điện thoại liên hệ");
        }

        String normalizedPhone = null;
        if (hasPhone) {
            if (!PhoneUtil.isValid(request.getContactPhone())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Số điện thoại không hợp lệ");
            }
            normalizedPhone = PhoneUtil.normalize(request.getContactPhone());
        }

        User user = getById(userId);

        // Đồng bộ số điện thoại tài khoản (kiểm tra trùng với người dùng khác, bỏ qua chính mình).
        if (normalizedPhone != null && !normalizedPhone.equals(user.getPhone())) {
            userRepository.findByPhoneAndDeletedAtIsNull(normalizedPhone).ifPresent(other -> {
                if (!other.getId().equals(userId)) {
                    throw new ConflictException(ErrorCode.PHONE_ALREADY_EXISTS);
                }
            });
            user.setPhone(normalizedPhone);
            userRepository.save(user);
        }

        // Nếu là chủ trọ thì cập nhật thông tin liên hệ trên hồ sơ chủ trọ.
        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        boolean landlordUpdated = false;
        if (lp != null) {
            if (hasName) {
                lp.setContactName(request.getContactName().trim());
            }
            if (normalizedPhone != null) {
                lp.setContactPhone(normalizedPhone);
            }
            landlordProfileRepository.save(lp);
            landlordUpdated = true;
        }

        return ContactInfoUpdateResponse.builder()
                .phone(user.getPhone())
                .contactName(lp != null ? lp.getContactName() : null)
                .contactPhone(lp != null ? lp.getContactPhone() : null)
                .landlordProfileUpdated(landlordUpdated)
                .build();
    }

    @Override
    @Transactional
    public AvatarResponse updateAvatar(Long userId, MultipartFile file) {
        User user = getById(userId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.AVATAR_INVALID_FORMAT, "Chưa chọn tệp ảnh");
        }
        long maxBytes = (long) systemConfigService.getInt(ConfigKey.LISTING_IMAGE_MAX_SIZE_MB) * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.AVATAR_TOO_LARGE);
        }
        if (!isAllowedImage(file)) {
            throw new BusinessException(ErrorCode.AVATAR_INVALID_FORMAT);
        }

        // FileStorage kiểm tra lại magic bytes + chống decompression bomb + sinh thumbnail.
        var stored = fileStorage.storeImage(file, AVATAR_SUBDIR + "/" + userId);
        user.setAvatarUrl(stored.getOriginalUrl());
        userRepository.save(user);

        return AvatarResponse.builder()
                .avatarUrl(stored.getOriginalUrl())
                .thumbnailUrl(stored.getThumbnailUrl())
                .build();
    }

    @Override
    @Transactional
    public void deleteAvatar(Long userId) {
        User user = getById(userId);
        if (user.getAvatarUrl() == null) {
            // Gọi khi đã không còn avatar → 404, không trả 204 khống (mục 4.2.13 quy tắc 5).
            throw new ResourceNotFoundException(ErrorCode.AVATAR_NOT_FOUND);
        }
        user.setAvatarUrl(null);
        userRepository.save(user);
        // Xóa file vật lý là best-effort (mục 4.2.13 quy tắc 2). FileStorage không cung cấp phép
        // nghịch URL→key nên không xóa được biến thể theo URL đã lưu; cột NULL là nguồn sự thật.
    }

    // ========================== Hồ sơ chủ trọ ==========================

    @Override
    @Transactional(readOnly = true)
    public MyLandlordProfileResponse getMyLandlordProfile(Long userId) {
        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANDLORD_PROFILE_NOT_FOUND));
        return toMyLandlordResponse(lp);
    }

    @Override
    @Transactional
    public MyLandlordProfileResponse updateMyLandlordProfile(Long userId, UpdateLandlordProfileRequest request) {
        User user = getById(userId);
        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseGet(() -> LandlordProfile.builder().user(user).build());

        lp.setContactName(request.getContactName().trim());
        lp.setContactPhone(PhoneUtil.normalize(request.getContactPhone()));
        lp.setContactEmail(request.getContactEmail());
        lp.setDisplayName(sanitizeNullable(request.getDisplayName()));
        lp.setCompanyName(sanitizeNullable(request.getBusinessName()));
        lp.setAddress(sanitizeNullable(request.getBusinessAddress()));
        if (request.getChatEnabled() != null) {
            lp.setAllowChat(request.getChatEnabled());
        }
        landlordProfileRepository.save(lp);
        return toMyLandlordResponse(lp);
    }

    @Override
    @Transactional
    public LandlordVerificationResponse submitLandlordVerification(Long userId,
                                                                   LandlordVerificationRequest request) {
        User user = getById(userId);
        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANDLORD_PROFILE_NOT_FOUND));

        if (lp.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new ConflictException(ErrorCode.LANDLORD_ALREADY_VERIFIED);
        }

        // Đặt hồ sơ về trạng thái chờ duyệt + lưu ghi chú gửi Admin.
        lp.setVerificationStatus(VerificationStatus.PENDING);
        lp.setVerificationNote(sanitizeNullable(request.getNote()));
        landlordProfileRepository.save(lp);

        Instant now = Instant.now();
        Verification verification = Verification.builder()
                .user(user)
                .type(VerificationType.LANDLORD)
                .status(VerificationStatus.PENDING)
                .attemptCount(0)
                // Xác thực chủ trọ do Admin duyệt thủ công; đặt hạn xử lý 30 ngày (cột NOT NULL).
                .expiresAt(now.plus(30, ChronoUnit.DAYS))
                .build();
        verification = verificationRepository.save(verification);

        return LandlordVerificationResponse.builder()
                .verificationId(verification.getId())
                .type(VerificationType.LANDLORD)
                .status(VerificationStatus.PENDING)
                .submittedAt(verification.getCreatedAt() != null ? verification.getCreatedAt() : now)
                .build();
    }

    // ==================== API cho module khác gọi ====================

    @Override
    @Transactional(readOnly = true)
    public LandlordPublicResponse getPublicProfile(Long targetUserId, Long currentUserId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        // LOCKED/DELETED không lộ ra ngoài cho người thường (mục 4.2.5 quy tắc 6).
        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        // Chủ trọ hay không CHỈ do vai trò quyết định. Trước đây còn xét "có hồ sơ chủ trọ" —
        // điều đó khiến hai người cùng vai trò lại hiển thị/hoạt động khác nhau (ví dụ người
        // từng là chủ trọ rồi bị hạ vai trò vẫn còn hồ sơ sót lại).
        boolean isLandlord = RoleCode.LANDLORD.equals(
                roleRepository.findRoleCodeByUserId(targetUserId).orElse(null));
        LandlordProfile lp = isLandlord
                ? landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(targetUserId).orElse(null)
                : null;

        long followerCount = followRepository.countByLandlord_IdAndDeletedAtIsNull(targetUserId);
        boolean followedByMe = currentUserId != null
                && !currentUserId.equals(targetUserId)
                && followRepository.existsByFollower_IdAndLandlord_IdAndDeletedAtIsNull(currentUserId, targetUserId);
        boolean viewerAuthenticated = currentUserId != null;

        TrustLabel trustLabel = null;
        if (lp != null && lp.getTrustScore() != null) {
            trustLabel = trustScoreService.labelOf(toInt(lp.getTrustScore()));
        }

        return landlordProfileMapper.toPublicResponse(user, lp, isLandlord, followerCount,
                followedByMe, viewerAuthenticated, trustLabel);
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long userId) {
        return userId != null && userRepository.findByIdAndDeletedAtIsNull(userId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public UserRefResponse loadUserRef(Long userId) {
        return userMapper.toRef(getById(userId));
    }

    @Override
    @Transactional
    public void suspendPosting(Long userId, Instant until, String reason) {
        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        if (lp == null) {
            // Người dùng chưa phải chủ trọ → không có tin để tạm khóa; ghi log để lần vết kiểm duyệt.
            log.warn("Bỏ qua tạm khóa đăng tin cho user {} vì không có hồ sơ chủ trọ", userId);
            return;
        }
        lp.setPostingRestrictedUntil(until);
        lp.setRestrictReason(reason);
        landlordProfileRepository.save(lp);
    }

    // ============================== Nội bộ ==============================

    /** Dựng response hồ sơ chủ trọ của tôi, tính cờ tạm khóa đăng tin. */
    private MyLandlordProfileResponse toMyLandlordResponse(LandlordProfile lp) {
        Instant restrictedUntil = lp.getPostingRestrictedUntil();
        boolean suspended = restrictedUntil != null && restrictedUntil.isAfter(Instant.now());
        return landlordProfileMapper.toMyResponse(lp, suspended, suspended ? restrictedUntil : null);
    }

    private void validateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return;
        }
        if (Period.between(dateOfBirth, LocalDate.now()).getYears() < MIN_AGE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Bạn phải đủ " + MIN_AGE + " tuổi trở lên");
        }
    }

    private String sanitizeNullable(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = HtmlSanitizer.stripAllHtml(input);
        return cleaned.isBlank() ? null : cleaned;
    }

    /** Kiểm nhanh magic bytes JPG/PNG/WEBP (phòng thủ sớm trước khi giao cho FileStorage). */
    private boolean isAllowedImage(MultipartFile file) {
        try {
            byte[] h = new byte[12];
            int read = file.getInputStream().read(h);
            if (read < 12) {
                return false;
            }
            boolean jpg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            return jpg || png || webp;
        } catch (Exception e) {
            log.warn("Không đọc được magic bytes ảnh đại diện: {}", e.getMessage());
            return false;
        }
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
