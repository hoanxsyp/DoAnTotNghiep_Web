package com.webtro.modules.user.dto.request;

import com.webtro.common.enums.Gender;
import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Yêu cầu cập nhật hồ sơ cá nhân — {@code PUT /api/users/me} (USER-02, docs/03 mục 4.2.2).
 *
 * <p>{@code email}/{@code phone} KHÔNG sửa được ở đây (phải qua luồng xác thực riêng);
 * {@code bio}/{@code address} sẽ được {@code HtmlSanitizer} làm sạch trước khi lưu.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateProfileRequest", description = "Dữ liệu cập nhật hồ sơ cá nhân")
public class UpdateProfileRequest {

    /** Họ tên đầy đủ {@code [§3.1]}. */
    @Schema(description = "Họ tên đầy đủ", example = "Nguyễn Văn An")
    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Họ tên chỉ được chứa chữ cái, khoảng trắng và dấu . ' -")
    private String fullName;

    /** Giới tính {@code [§6.3]} (tùy chọn). */
    @Schema(description = "Giới tính", example = "MALE")
    private Gender gender;

    /** Ngày sinh (tùy chọn) — phải là quá khứ, tuổi tối thiểu 16 (kiểm tra ở service). */
    @Schema(description = "Ngày sinh (yyyy-MM-dd)", example = "1998-05-20")
    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dateOfBirth;

    /** Địa chỉ thường trú (tùy chọn). */
    @Schema(description = "Địa chỉ thường trú", example = "12 Nguyễn Huệ, P. Bến Nghé, Q.1, TP. Hồ Chí Minh")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    @NoHtml
    private String address;

    /** Giới thiệu bản thân (tùy chọn). */
    @Schema(description = "Giới thiệu bản thân", example = "Đang tìm phòng trọ khu Bình Thạnh, ngân sách 3-4 triệu.")
    @Size(max = 500, message = "Giới thiệu tối đa 500 ký tự")
    @NoHtml
    private String bio;
}
