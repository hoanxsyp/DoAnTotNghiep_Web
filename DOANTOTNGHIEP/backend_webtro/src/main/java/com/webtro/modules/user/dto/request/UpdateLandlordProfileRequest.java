package com.webtro.modules.user.dto.request;

import com.webtro.validator.NoHtml;
import com.webtro.validator.ValidPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu cập nhật hồ sơ chủ trọ — {@code PUT /api/users/me/landlord-profile}
 * (docs/03 mục 4.2.11, {@code [§7.3]}).
 *
 * <p><b>Lưu ý ánh xạ thực thể:</b> tài liệu 03 liệt kê {@code contactZalo} và {@code description}
 * nhưng bảng {@code landlord_profiles} không có hai cột đó — thực thể có {@code contactEmail},
 * {@code displayName}, {@code companyName} (businessName) và {@code address} (businessAddress).
 * DTO này bám theo cột thật của thực thể (không sửa entity theo yêu cầu đề bài).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateLandlordProfileRequest", description = "Dữ liệu cập nhật hồ sơ chủ trọ")
public class UpdateLandlordProfileRequest {

    /** Tên người liên hệ hiển thị trên tin {@code [§3.3]}. */
    @Schema(description = "Tên người liên hệ", example = "Anh An")
    @NotBlank(message = "Vui lòng nhập tên người liên hệ")
    @Size(min = 2, max = 100, message = "Tên người liên hệ phải từ 2 đến 100 ký tự")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Tên người liên hệ chỉ được chứa chữ cái, khoảng trắng và dấu . ' -")
    private String contactName;

    /** Số điện thoại liên hệ {@code [§3.3]}. */
    @Schema(description = "Số điện thoại liên hệ", example = "0901234456")
    @NotBlank(message = "Vui lòng nhập số điện thoại liên hệ")
    @ValidPhone
    private String contactPhone;

    /** Email liên hệ (tùy chọn). */
    @Schema(description = "Email liên hệ", example = "an.nguyen@gmail.com")
    @Email(message = "Email liên hệ không hợp lệ")
    @Size(max = 190, message = "Email liên hệ tối đa 190 ký tự")
    private String contactEmail;

    /** Tên hiển thị công khai (tùy chọn). */
    @Schema(description = "Tên hiển thị công khai", example = "Nhà trọ An Bình")
    @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
    @NoHtml
    private String displayName;

    /** Tên cơ sở/doanh nghiệp (tùy chọn) — ánh xạ cột {@code company_name}. */
    @Schema(description = "Tên cơ sở/doanh nghiệp", example = "Nhà trọ An Bình")
    @Size(max = 150, message = "Tên cơ sở tối đa 150 ký tự")
    @NoHtml
    private String businessName;

    /** Địa chỉ cơ sở (tùy chọn) — ánh xạ cột {@code address}. */
    @Schema(description = "Địa chỉ cơ sở", example = "45/12 Đường D2, P.25, Q. Bình Thạnh, TP. Hồ Chí Minh")
    @Size(max = 255, message = "Địa chỉ cơ sở tối đa 255 ký tự")
    @NoHtml
    private String businessAddress;

    /** Bật/tắt chat với người thuê {@code [§3.10]} (tùy chọn, mặc định giữ nguyên). */
    @Schema(description = "Cho phép chat với người thuê", example = "true")
    private Boolean chatEnabled;
}
