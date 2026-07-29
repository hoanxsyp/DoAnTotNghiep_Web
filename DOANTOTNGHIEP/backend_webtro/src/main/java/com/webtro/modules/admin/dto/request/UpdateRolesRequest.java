package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Yêu cầu cập nhật vai trò người dùng (canonical 4.13.5). {@code roles} là tập THAY THẾ toàn bộ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateRolesRequest", description = "Cập nhật vai trò người dùng (thay thế toàn bộ)")
public class UpdateRolesRequest {

    @Schema(description = "Tập vai trò mới (mã ∈ RoleCode)", example = "[\"ROLE_TENANT\",\"ROLE_LANDLORD\"]")
    @NotEmpty(message = "Danh sách vai trò không được rỗng")
    private List<String> roles;

    @Schema(description = "Lý do thay đổi (10–500 ký tự, để ghi audit)")
    @NotBlank(message = "Vui lòng nhập lý do thay đổi vai trò")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;
}
