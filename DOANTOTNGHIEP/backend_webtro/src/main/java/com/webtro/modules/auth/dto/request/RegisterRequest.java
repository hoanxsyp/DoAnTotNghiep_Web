package com.webtro.modules.auth.dto.request;

import com.webtro.validator.ValidPassword;
import com.webtro.validator.ValidPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu đăng ký tài khoản (AUTH-01) — {@code POST /api/auth/register}.
 *
 * <p>Ràng buộc theo docs/03 mục 4.1.1. Khi {@code requestedRole = LANDLORD} thì {@code contactPhone}
 * và {@code contactName} là bắt buộc (kiểm tra ở tầng service, không ở annotation vì là điều kiện).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RegisterRequest", description = "Dữ liệu đăng ký tài khoản")
public class RegisterRequest {

    /** Vai trò mong muốn khi đăng ký. */
    public enum RequestedRole {
        TENANT, LANDLORD
    }

    @Schema(description = "Họ tên đầy đủ", example = "Nguyễn Văn An")
    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    @Pattern(regexp = "^[\\p{L} .'\\-]+$", message = "Họ tên chỉ được chứa chữ, khoảng trắng và các ký tự . ' -")
    private String fullName;

    @Schema(description = "Email đăng nhập", example = "nguyen.van.an@gmail.com")
    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;

    @Schema(description = "Số điện thoại di động Việt Nam", example = "0901234567")
    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @ValidPhone
    private String phone;

    @Schema(description = "Mật khẩu (tối thiểu 8 ký tự, có chữ và số)", example = "MatKhau123")
    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @ValidPassword
    private String password;

    @Schema(description = "Nhập lại mật khẩu để xác nhận", example = "MatKhau123")
    @NotBlank(message = "Vui lòng xác nhận mật khẩu")
    private String confirmPassword;

    @Schema(description = "Vai trò mong muốn: TENANT (người thuê) hoặc LANDLORD (chủ trọ)", example = "TENANT")
    @NotNull(message = "Vui lòng chọn vai trò đăng ký")
    private RequestedRole requestedRole;

    @Schema(description = "Số điện thoại liên hệ hiển thị trên tin — bắt buộc khi đăng ký chủ trọ",
            example = "0901234567")
    private String contactPhone;

    @Schema(description = "Tên người liên hệ — bắt buộc khi đăng ký chủ trọ", example = "Nguyễn Văn An")
    private String contactName;

    @Schema(description = "Đồng ý điều khoản sử dụng", example = "true")
    @AssertTrue(message = "Bạn phải đồng ý với điều khoản sử dụng")
    private boolean acceptTerms;
}
