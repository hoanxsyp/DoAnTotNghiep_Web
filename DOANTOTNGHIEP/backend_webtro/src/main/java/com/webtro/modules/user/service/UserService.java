package com.webtro.modules.user.service;

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
import com.webtro.modules.user.entity.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nghiệp vụ người dùng: hồ sơ cá nhân, hồ sơ công khai, avatar, hồ sơ chủ trọ (USER-01..06).
 *
 * <p>Nhóm method cuối ({@link #getById}, {@link #existsById}, {@link #getPublicProfile},
 * {@link #loadUserRef}) là API cho MODULE KHÁC gọi (canonical luật 4): listing/interaction lấy
 * tên/avatar chủ trọ, kiểm tra tồn tại người dùng...
 */
public interface UserService {

    // ============================ Hồ sơ của tôi ============================

    /** Hồ sơ cá nhân đầy đủ của chính chủ tài khoản (USER-01). */
    UserProfileResponse getMyProfile(Long userId);

    /** Cập nhật hồ sơ cá nhân (USER-02). */
    UserProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request);

    /**
     * Cập nhật nhanh thông tin liên hệ: {@code contactName}/{@code contactPhone} trên hồ sơ chủ trọ
     * (nếu có) và đồng bộ {@code phone} tài khoản. Bắt buộc ít nhất một trường.
     */
    ContactInfoUpdateResponse updateContact(Long userId, UpdateContactRequest request);

    /** Cập nhật ảnh đại diện (USER-02). */
    AvatarResponse updateAvatar(Long userId, MultipartFile file);

    /** Xóa ảnh đại diện (mục 4.2.13). */
    void deleteAvatar(Long userId);

    // ========================== Hồ sơ chủ trọ ==========================

    /** Hồ sơ chủ trọ của chính mình (mục 4.2.10). */
    MyLandlordProfileResponse getMyLandlordProfile(Long userId);

    /** Cập nhật hồ sơ chủ trọ của chính mình (mục 4.2.11). */
    MyLandlordProfileResponse updateMyLandlordProfile(Long userId, UpdateLandlordProfileRequest request);

    /** Gửi yêu cầu xác thực chủ trọ (USER-06, mục 4.2.12). */
    LandlordVerificationResponse submitLandlordVerification(Long userId, LandlordVerificationRequest request);

    // ==================== API cho module khác gọi ====================

    /** Hồ sơ chủ trọ công khai (USER-04). {@code currentUserId} null nếu khách chưa đăng nhập. */
    LandlordPublicResponse getPublicProfile(Long targetUserId, Long currentUserId);

    /** Lấy entity người dùng còn sống theo id, ném {@code USER_NOT_FOUND} nếu không có. */
    User getById(Long userId);

    /** Người dùng còn sống có tồn tại không. */
    boolean existsById(Long userId);

    /** Tham chiếu rút gọn (id + tên + avatar) cho listing/interaction. */
    UserRefResponse loadUserRef(Long userId);

    /**
     * Tạm khóa chức năng đăng tin của một chủ trọ tới thời điểm {@code until} (canonical §5.4) —
     * do module {@code moderation} gọi qua SPI. Đặt {@code landlord_profiles.posting_restricted_until}
     * và lý do. Không có hồ sơ chủ trọ thì bỏ qua (người dùng không phải chủ trọ nên không đăng tin).
     */
    void suspendPosting(Long userId, java.time.Instant until, String reason);
}
