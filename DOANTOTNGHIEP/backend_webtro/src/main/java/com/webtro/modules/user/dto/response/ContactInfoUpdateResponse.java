package com.webtro.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả cập nhật nhanh thông tin liên hệ ({@code PATCH /api/users/me/contact}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ContactInfoUpdateResponse", description = "Thông tin liên hệ sau cập nhật")
public class ContactInfoUpdateResponse {

    /** Số điện thoại tài khoản. */
    private String phone;

    /** Tên liên hệ (chủ trọ). */
    private String contactName;

    /** Số điện thoại liên hệ (chủ trọ). */
    private String contactPhone;

    /** Có cập nhật hồ sơ chủ trọ hay không. */
    private boolean landlordProfileUpdated;
}
