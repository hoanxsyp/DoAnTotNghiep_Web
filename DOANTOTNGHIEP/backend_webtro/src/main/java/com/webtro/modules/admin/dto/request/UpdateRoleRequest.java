package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu đổi vai trò người dùng (canonical 4.13.5).
 *
 * <p>Mỗi người dùng có ĐÚNG MỘT vai trò nên đây là giá trị THAY THẾ, không phải tập hợp.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateRoleRequest", description = "Đổi vai trò người dùng")
public class UpdateRoleRequest {

    @Schema(description = "Vai trò mới (mã ∈ RoleCode)", example = "ROLE_LANDLORD")
    @NotBlank(message = "Vui lòng chọn vai trò")
    private String role;

    @Schema(description = "Lý do thay đổi (10–500 ký tự, để ghi audit)")
    @NotBlank(message = "Vui lòng nhập lý do thay đổi vai trò")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;
}
