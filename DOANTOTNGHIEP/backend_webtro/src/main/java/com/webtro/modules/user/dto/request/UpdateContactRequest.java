package com.webtro.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho {@code PATCH /api/users/me/contact}. Cập nhật nhanh thông tin liên hệ: {@code contactName}
 * / {@code contactPhone} trên hồ sơ chủ trọ (nếu là chủ trọ), đồng thời đồng bộ {@code phone} của
 * tài khoản. Các trường đều tùy chọn nhưng phải có ít nhất một trường.
 */
@Getter
@Setter
@Schema(name = "UpdateContactRequest", description = "Cập nhật nhanh thông tin liên hệ")
public class UpdateContactRequest {

    @Schema(description = "Tên người liên hệ (chỉ áp dụng cho chủ trọ)", example = "Nguyễn Văn A")
    @Size(max = 100, message = "Tên liên hệ tối đa 100 ký tự")
    private String contactName;

    @Schema(description = "Số điện thoại liên hệ", example = "0901234567")
    @Size(max = 15, message = "Số điện thoại tối đa 15 ký tự")
    private String contactPhone;
}
