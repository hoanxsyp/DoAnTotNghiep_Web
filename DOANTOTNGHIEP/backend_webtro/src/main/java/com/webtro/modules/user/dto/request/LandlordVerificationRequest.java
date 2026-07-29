package com.webtro.modules.user.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gửi xác thực chủ trọ — {@code POST /api/users/me/landlord-verification}
 * (USER-06, docs/03 mục 4.2.12). Xác thực THỦ CÔNG bởi Admin/Moderator {@code [§13.3]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LandlordVerificationRequest", description = "Ghi chú gửi kèm yêu cầu xác thực chủ trọ")
public class LandlordVerificationRequest {

    /** Ghi chú gửi Admin (tùy chọn). */
    @Schema(description = "Ghi chú gửi quản trị viên", example = "Tôi có 6 phòng cho thuê tại Bình Thạnh.")
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    @NoHtml
    private String note;
}
