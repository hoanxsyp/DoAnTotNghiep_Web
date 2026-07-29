package com.webtro.modules.admin.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.UserStatus;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.modules.admin.dto.request.LockUserRequest;
import com.webtro.modules.admin.dto.request.RejectLandlordVerificationRequest;
import com.webtro.modules.admin.dto.request.RestrictLandlordPostingRequest;
import com.webtro.modules.admin.dto.request.UnlockUserRequest;
import com.webtro.modules.admin.dto.request.UnverifyLandlordRequest;
import com.webtro.modules.admin.dto.request.UpdateRolesRequest;
import com.webtro.modules.admin.dto.request.VerifyLandlordRequest;
import com.webtro.modules.admin.dto.response.AdminLandlordResponse;
import com.webtro.modules.admin.dto.response.AdminUserDetailResponse;
import com.webtro.modules.admin.dto.response.AdminUserResponse;
import com.webtro.modules.admin.dto.response.LandlordPostingRestrictionResponse;
import com.webtro.modules.admin.dto.response.LandlordVerificationActionResponse;
import com.webtro.modules.admin.dto.response.UserActionResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Quản trị người dùng (canonical 4.13): tìm/lọc ({@code USER_MANAGE}), khóa/mở khóa
 * ({@code USER_MANAGE}), đổi vai trò ({@code USER_ROLE_ASSIGN}). Kiểm quyền permission ở controller;
 * kiểm quy tắc "không tự khóa/không sửa Admin khác" ở service này.
 */
public interface AdminUserService {

    /** Tìm/lọc người dùng (canonical 4.13.1). */
    PageResponse<AdminUserResponse> searchUsers(String keyword, List<String> roles,
                                                List<UserStatus> statuses, Boolean verified,
                                                Instant from, Instant to, Pageable pageable);

    /** Khóa tài khoản (canonical 4.13.3): ghi audit + thu hồi phiên + (tùy chọn) khóa tin + thông báo. */
    UserActionResponse lockUser(Long userId, LockUserRequest request, Long actorId);

    /** Mở khóa tài khoản (canonical 4.13.4). */
    UserActionResponse unlockUser(Long userId, UnlockUserRequest request, Long actorId);

    /** Cập nhật vai trò (canonical 4.13.5): thay thế toàn bộ tập vai trò + ghi audit. */
    UserActionResponse updateRoles(Long userId, UpdateRolesRequest request, Long actorId);

    /** Chi tiết một người dùng cho quản trị (canonical mục 4.13): hồ sơ + vai trò + số liệu liên quan. */
    AdminUserDetailResponse getUserDetail(Long userId);

    // ============================ Chủ trọ (LANDLORD_VERIFY) ============================

    /** Danh sách chủ trọ có lọc/phân trang (canonical 4.13.6). */
    PageResponse<AdminLandlordResponse> listLandlords(String keyword,
                                                      List<VerificationStatus> verificationStatuses,
                                                      Integer minTrustScore, Integer maxTrustScore,
                                                      Boolean postingSuspended, Pageable pageable);

    /** Xác thực chủ trọ (canonical 4.13.7): đặt VERIFIED + ghi audit + thông báo. */
    LandlordVerificationActionResponse verifyLandlord(Long userId, VerifyLandlordRequest request, Long actorId);

    /** Hủy xác thực chủ trọ (canonical 4.13.8): về PENDING + ghi audit + thông báo. */
    LandlordVerificationActionResponse unverifyLandlord(Long userId, UnverifyLandlordRequest request, Long actorId);

    /**
     * Từ chối yêu cầu xác thực chủ trọ đang chờ duyệt: đặt REJECTED + ghi audit + thông báo (bắt
     * buộc lý do). Nếu hồ sơ đã VERIFIED thì dùng hủy xác thực thay vì từ chối.
     */
    LandlordVerificationActionResponse rejectLandlordVerification(Long userId,
                                                                 RejectLandlordVerificationRequest request,
                                                                 Long actorId);

    /**
     * Tạm hạn chế chức năng đăng tin của chủ trọ tới thời điểm chỉ định: đặt
     * {@code posting_restricted_until} + lý do + ghi audit + thông báo.
     */
    LandlordPostingRestrictionResponse restrictLandlordPosting(Long userId,
                                                               RestrictLandlordPostingRequest request,
                                                               Long actorId);
}
