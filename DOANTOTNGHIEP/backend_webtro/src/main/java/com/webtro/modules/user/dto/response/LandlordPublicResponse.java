package com.webtro.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.webtro.common.enums.TrustLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hồ sơ chủ trọ công khai — {@code GET /api/users/{id}} (USER-04, docs/03 mục 4.2.5 & 5.5).
 *
 * <p>Che dữ liệu nhạy cảm theo §5.7: KHÔNG bao giờ chứa {@code email}, {@code dateOfBirth},
 * {@code address}, {@code status}. Số điện thoại bị che (MaskUtil) với khách chưa đăng nhập.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LandlordPublicResponse", description = "Hồ sơ chủ trọ công khai")
public class LandlordPublicResponse {

    @Schema(description = "Id người dùng", example = "42")
    private Long id;

    @Schema(description = "Họ tên", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "URL ảnh đại diện (null nếu chưa có)")
    private String avatarUrl;

    @Schema(description = "Có phải chủ trọ không", example = "true")
    @JsonProperty("isLandlord")
    private boolean isLandlord;

    @Schema(description = "Đã được xác minh chưa", example = "true")
    private boolean verified;

    @Schema(description = "Điểm uy tín (0-100)", example = "87")
    private Integer trustScore;

    @Schema(description = "Nhãn uy tín suy ra từ điểm", example = "GOOD")
    private TrustLabel trustLabel;

    @Schema(description = "Điểm đánh giá trung bình", example = "4.5")
    private BigDecimal averageRating;

    @Schema(description = "Tổng số lượt đánh giá", example = "23")
    private Integer totalReviews;

    @Schema(description = "Số tin đang hoạt động", example = "4")
    private Integer totalActiveListings;

    @Schema(description = "Số tin đã đóng (suy ra: tổng - đang hoạt động)", example = "9")
    private Integer totalClosedListings;

    @Schema(description = "Tên người liên hệ", example = "Anh An")
    private String contactName;

    @Schema(description = "Số điện thoại liên hệ (che nếu chưa đăng nhập)", example = "0901***456")
    private String contactPhone;

    @Schema(description = "Số điện thoại có đang bị che không", example = "true")
    private boolean phoneMasked;

    @Schema(description = "Cho phép chat", example = "true")
    private boolean chatEnabled;

    @Schema(description = "Tỷ lệ phản hồi (%)", example = "92")
    private Integer responseRatePercent;

    @Schema(description = "Số người theo dõi", example = "58")
    private long followerCount;

    @Schema(description = "Người xem hiện tại có đang theo dõi không (khách → false)", example = "false")
    private boolean followedByMe;

    @Schema(description = "Thành viên từ", example = "2026-01-14T08:00:00Z")
    private Instant memberSince;
}
